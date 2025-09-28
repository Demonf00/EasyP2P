package com.easy.ui;

import com.easy.net.InviteCodec;
import com.easy.net.NetClient;
import com.easy.net.NetServer;
import com.easy.net.UpnpHelper;

import javax.swing.*;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class SidebarPanel extends JPanel {
    private java.util.function.Consumer<Boolean> roleCb; // true=host, false=client
    public void onRoleKnown(java.util.function.Consumer<Boolean> cb){ this.roleCb = cb; }

    private final ConsoleSink log;
    private final JTextField inviteIn  = new JTextField();
    private final JTextField inviteOut = new JTextField();
    private final JTextField portField = new JTextField("2266");

    private final JRadioButton lanBtn = new JRadioButton("局域网");
    private final JRadioButton wanBtn = new JRadioButton("公网", true);

    private NetServer server;
    private NetClient client;
    private MoveSender currentSender;
    private BoardCanvas board;

    public SidebarPanel(ConsoleSink log) {
        this.log = log;
        setLayout(new GridLayout(0,1,6,6));

        Dimension btnSize = new Dimension(140, 28);
        inviteIn.setPreferredSize(new Dimension(180, 26));
        inviteOut.setPreferredSize(new Dimension(180, 26));
        portField.setPreferredSize(new Dimension(120, 26));

        ButtonGroup bg = new ButtonGroup(); bg.add(lanBtn); bg.add(wanBtn);

        JButton serverBtn = new JButton("我是服务器(生成码)");
        serverBtn.setPreferredSize(btnSize);
        serverBtn.addActionListener(e -> startServerAndGenInvite());

        JButton clientBtn = new JButton("我是客户端(粘贴码连接)");
        clientBtn.setPreferredSize(btnSize);
        clientBtn.addActionListener(e -> connectByInvite());

        JButton copyBtn = new JButton("复制邀请码");
        copyBtn.setPreferredSize(btnSize);
        copyBtn.addActionListener(e -> {
            String text = inviteOut.getText().replaceAll("\\s+","");
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new java.awt.datatransfer.StringSelection(text), null);
            log.println("邀请码已复制到剪贴板");
        });
        inviteOut.setEditable(false);

        add(new JLabel("连接模式")); add(lanBtn); add(wanBtn);
        add(new JLabel("端口(建议 30000-60000)：")); add(portField);
        add(serverBtn);
        add(new JLabel("邀请码输出")); add(inviteOut); add(copyBtn);
        add(new JLabel("输入邀请码连接")); add(inviteIn); add(clientBtn);

        JButton resetBtn = new JButton("重置整局");
        resetBtn.setPreferredSize(btnSize);
        resetBtn.addActionListener(e -> doReset());
        add(resetBtn);

        add(new JLabel("提示：初始阶段仅连接，不预先选择棋类（房主稍后选择）。"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { if (server != null) server.closeUpnpIfAny(); } catch (Exception ignore) {}
        }));
    }

    public void setBoard(BoardCanvas board){ this.board = board; }
    public MoveSender getCurrentSender(){ return currentSender; }

    private int pickPort() {
        try {
            int p = Integer.parseInt(portField.getText().trim());
            if (p < 1024 || p > 65535) throw new IllegalArgumentException();
            return p;
        } catch (Exception e) {
            log.println("端口无效，回退到 35211");
            portField.setText("35211");
            return 35211;
        }
    }

    /** 公网：先 UPnP 映射成功再启动监听；LAN：直接监听 */
    private void startServerAndGenInvite(){
        int port = pickPort();
        boolean isLan = lanBtn.isSelected();
        try {
            String ip = isLan ? getLanIPv4() : fetchPublicIP();
            if (ip == null) { log.println("无法获取" + (isLan? "局域网":"公网") + "IP。"); return; }

            if (!isLan) {
                boolean ok = UpnpHelper.openTcp(port, "easy-p2p");
                if (!ok) { log.println("UPnP 映射失败：请在路由器手动把 " + port + "/TCP 映射到本机，然后重试。"); return; }
                log.println("UPnP 端口映射成功: " + port + "/TCP");
            }

            server = new NetServer(port, log,
                    (x,y) -> { if (board != null) board.onOpponentMove(x,y); },
                    (com.easy.ui.NetEventListener) board);
            server.setUpnpEnabled(!isLan);
            server.start();

            String code = InviteCodec.gen(ip, port);
            inviteOut.setText(code);

            String mode = isLan ? "局域网" : "公网";
            String note = isLan ? "（LAN 使用默认出网接口）" : "（已通过 UPnP 映射端口）";
            log.println(mode + " IP " + ip + " 端口 " + port + " 已生成邀请码 " + note);

            currentSender = server;
            if (board != null) board.setHost(true);
            if (roleCb != null) roleCb.accept(true);
        } catch (Exception ex) {
            log.println("生成邀请码或启动服务器失败: " + ex.getMessage());
            try { if (!isLan) UpnpHelper.closeTcp(port); } catch (Exception ignore){}
        }
    }

    private void connectByInvite(){
        try {
            client = new NetClient(log,
                    (x,y) -> { if (board != null) board.onOpponentMove(x,y); },
                    (com.easy.ui.NetEventListener) board);
            client.connect(inviteIn.getText().replaceAll("\\s+",""));
            log.println("连接已建立");
            currentSender = client;
            if (board != null) board.setHost(false);
            if (roleCb != null) roleCb.accept(false);
        } catch (Exception ex){
            log.println("连接失败: " + ex.getMessage());
        }
    }

    private void doReset() {
        try {
            if (currentSender instanceof NetServer) {
                if (board != null) board.manualReset(true);
                ((NetServer) currentSender).sendJson(com.easy.net.Proto.resetRound());
                log.println("已重置整局（host先手）并通知客户端");
            } else {
                log.println("当前不是房主，无法直接重置");
            }
        } catch (Exception ex) {
            log.println("重置失败: " + ex.getMessage());
        }
    }

    private static String fetchPublicIP() throws Exception {
        URL url = new URL("http://checkip.amazonaws.com/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000); conn.setReadTimeout(3000);
        try (Scanner sc = new Scanner(conn.getInputStream())) {
            return sc.nextLine().trim();
        }
    }
    private static String getLanIPv4() {
        try (java.net.DatagramSocket ds = new java.net.DatagramSocket()) {
            ds.connect(java.net.InetAddress.getByName("192.0.2.1"), 9);
            java.net.InetAddress local = ((java.net.InetSocketAddress) ds.getLocalSocketAddress()).getAddress();
            if (local instanceof java.net.Inet4Address && local.isSiteLocalAddress()) return local.getHostAddress();
        } catch (Exception ignore) {}
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                java.net.NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || nif.isPointToPoint()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress() && !a.isLoopbackAddress())
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignore) {}
        return null;
    }
}

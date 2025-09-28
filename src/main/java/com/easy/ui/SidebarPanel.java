package com.easy.ui;

import com.easy.net.InviteCodec;
import com.easy.net.NetClient;
import com.easy.net.NetServer;

import javax.swing.*;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class SidebarPanel extends JPanel {
    // 角色回调：true = host, false = client
    private java.util.function.Consumer<Boolean> roleCb;
    public void onRoleKnown(java.util.function.Consumer<Boolean> cb){ this.roleCb = cb; }

    private final ConsoleSink log;
    private final JTextField inviteIn  = new JTextField();
    private final JTextField inviteOut = new JTextField();
    private final JTextField portField = new JTextField("2266");

    // LAN / 公网
    private final JRadioButton lanBtn = new JRadioButton("局域网");
    private final JRadioButton wanBtn = new JRadioButton("公网", true);

    private NetServer   server;        // 仅保持一个
    private NetClient   client;        // 仅保持一个
    private MoveSender  currentSender; // 供棋盘发送落子
    private BoardCanvas board;

    public SidebarPanel(ConsoleSink log) {
        this.log = log;
        setLayout(new GridLayout(0,1,6,6));

        // 控件尺寸更紧凑
        Dimension btnSize = new Dimension(140, 28);
        inviteIn.setPreferredSize(new Dimension(180, 26));
        inviteOut.setPreferredSize(new Dimension(180, 26));
        portField.setPreferredSize(new Dimension(120, 26));

        ButtonGroup bg = new ButtonGroup();
        bg.add(lanBtn); bg.add(wanBtn);

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

        add(new JLabel("连接模式"));
        add(lanBtn);
        add(wanBtn);

        add(new JLabel("端口(建议 30000-60000)："));
        add(portField);

        add(serverBtn);
        add(new JLabel("邀请码输出"));
        add(inviteOut);
        add(copyBtn);
        add(new JLabel("输入邀请码连接"));
        add(inviteIn);
        add(clientBtn);

        // 重置按钮
        JButton resetBtn = new JButton("重置整局");
        resetBtn.setPreferredSize(btnSize);
        resetBtn.addActionListener(e -> doReset());
        add(resetBtn);

        add(new JLabel("提示：初始阶段仅连接，不预先选择棋类（房主稍后选择）。"));
    }

    public void setBoard(BoardCanvas board){
        this.board = board;
    }

    public MoveSender getCurrentSender(){
        return currentSender;
    }

    // ==== 动作 ====

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

    private void startServerAndGenInvite(){
        try {
            int port = pickPort();
            String ip = lanBtn.isSelected() ? getLanIPv4() : fetchPublicIP();
            if (ip == null) {
                log.println("无法获取" + (lanBtn.isSelected() ? "局域网" : "公网") + "IP，请检查网络。");
                return;
            }
            String code = InviteCodec.gen(ip, port);
            inviteOut.setText(code);
            log.println((lanBtn.isSelected() ? "局域网" : "公网") + " IP " + ip + " 端口 " + port + " 已生成邀请码（LAN 使用默认出网接口）");

            server = new NetServer(port, log,
                (x,y) -> { if (board != null) board.onOpponentMove(x,y); },
                (com.easy.ui.NetEventListener) board
            );
            server.start();
            currentSender = server;
            if (board != null) board.setHost(true);
            if (roleCb != null) roleCb.accept(true);
        } catch (Exception ex) {
            log.println("生成邀请码或启动服务器失败: " + ex.getMessage());
        }
    }

    private void connectByInvite(){
        try {
            client = new NetClient(log,
                (x,y) -> { if (board != null) board.onOpponentMove(x,y); },
                (com.easy.ui.NetEventListener) board
            );
            client.connect(inviteIn.getText().replaceAll("\\s+",""));
            log.println("连接已建立");
            currentSender = client;
            if (board != null) board.setHost(false);
            if (roleCb != null) roleCb.accept(false);
        } catch (Exception ex){
            log.println("连接失败: " + ex.getMessage());
        }
    }

    /** 房主手动重置整局（当前棋种，房主先手），并广播给客户端 */
    private void doReset() {
        try {
            if (currentSender instanceof NetServer) {
                if (board != null) board.manualReset(true); // host 先手
                ((NetServer) currentSender).sendJson(com.easy.net.Proto.resetRound());
                log.println("已重置整局（host先手）并通知客户端");
            } else if (currentSender instanceof NetClient) {
                log.println("你是客户端，不能直接重置（可建议房主重置）");
            } else {
                log.println("尚未连接，无法重置");
            }
        } catch (Exception ex) {
            log.println("重置失败: " + ex.getMessage());
        }
    }

    // ==== 辅助 ====

    private static String fetchPublicIP() throws Exception {
        URL url = new URL("http://checkip.amazonaws.com/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (Scanner sc = new Scanner(conn.getInputStream())) {
            return sc.nextLine().trim();
        }
    }

    /** 优先通过默认路由选取 IPv4（不会真的发包），失败再枚举物理网卡 */
    private static String getLanIPv4() {
        try (java.net.DatagramSocket ds = new java.net.DatagramSocket()) {
            ds.connect(java.net.InetAddress.getByName("192.0.2.1"), 9); // TEST-NET-1
            java.net.InetAddress local = ((java.net.InetSocketAddress) ds.getLocalSocketAddress()).getAddress();
            if (local instanceof java.net.Inet4Address && local.isSiteLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignore) { }
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                java.net.NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || nif.isPointToPoint()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress() && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignore) { }
        return null;
    }
}

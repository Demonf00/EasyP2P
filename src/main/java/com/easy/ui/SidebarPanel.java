package com.easy.ui;

import com.easy.net.InviteCodec;
import com.easy.net.NetClient;
import com.easy.net.NetServer;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.Enumeration;
import java.util.Scanner;

public class SidebarPanel extends JPanel {
    private final ConsoleSink log;
    private final JTextField inviteIn = new JTextField();
    private final JTextField inviteOut = new JTextField();
    private final JTextField portField = new JTextField("2266");

    // LAN/Public toggle
    private final JRadioButton lanBtn = new JRadioButton("局域网");
    private final JRadioButton wanBtn = new JRadioButton("公网", true);

    private NetServer server;       // only one at a time
    private NetClient client;       // only one at a time
    private MoveSender currentSender;

    private BoardCanvas board;

    public SidebarPanel(ConsoleSink log) {
        this.log = log;
        setLayout(new GridLayout(0,1,6,6));

        // Button defaults smaller
        Dimension btnSize = new Dimension(140, 28);

        // LAN/WAN group
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

        add(new JLabel("提示：初始阶段仅连接，不预先选择棋类（房主稍后选择）。"));
    }

    public void setBoard(BoardCanvas board){
        this.board = board;
    }

    public MoveSender getCurrentSender(){
        return currentSender;
    }

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
            log.println((lanBtn.isSelected() ? "局域网" : "公网") + " IP " + ip + " 端口 " + port + " 已生成邀请码");

            server = new NetServer(port, log, (x,y) -> {
                if (board != null) board.onOpponentMove(x,y);
            });
            server.start();
            currentSender = server;
        } catch (Exception ex) {
            log.println("生成邀请码或启动服务器失败: " + ex.getMessage());
        }
    }

    private void connectByInvite(){
        try {
            client = new NetClient(log, (x,y) -> {
                if (board != null) board.onOpponentMove(x,y);
            });
            client.connect(inviteIn.getText().replaceAll("\\s+",""));
            log.println("连接已建立");
            currentSender = client;
        } catch (Exception ex){
            log.println("连接失败: " + ex.getMessage());
        }
    }

    // --- helpers ---
    private static String fetchPublicIP() throws Exception {
        URL url = new URL("http://checkip.amazonaws.com/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (Scanner sc = new Scanner(conn.getInputStream())) {
            return sc.nextLine().trim();
        }
    }

    private static String getLanIPv4() throws Exception {
        // find a site-local IPv4 that's not loopback
        Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
        while (ifs.hasMoreElements()) {
            NetworkInterface nif = ifs.nextElement();
            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
            Enumeration<InetAddress> addrs = nif.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address && a.isSiteLocalAddress() && !a.isLoopbackAddress()) {
                    return a.getHostAddress();
                }
            }
        }
        return null;
    }
}

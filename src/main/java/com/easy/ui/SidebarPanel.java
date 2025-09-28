package com.easy.ui;

import com.easy.net.InviteCodec;
import com.easy.net.NetClient;
import com.easy.net.NetServer;
import com.easy.game.GameType;

import javax.swing.*;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class SidebarPanel extends JPanel {
    private final ConsoleSink log;
    private final JTextField inviteIn = new JTextField();
    private final JTextArea inviteOut = new JTextArea(4, 20);
    private GameType gameType = GameType.GOMOKU;

    private NetServer server;       // only one at a time
    private NetClient client;       // only one at a time
    private MoveSender currentSender;

    private BoardCanvas board;

    public SidebarPanel(ConsoleSink log) {
        this.log = log;
        setLayout(new GridLayout(0,1,6,6));

        JComboBox<GameType> gameSel = new JComboBox<>(GameType.values());
        gameSel.addActionListener(e-> gameType = (GameType) gameSel.getSelectedItem());

        JButton serverBtn = new JButton("我是服务器(生成邀请码)");
        serverBtn.addActionListener(e -> startServerAndGenInvite());

        JButton clientBtn = new JButton("我是客户端(粘贴邀请码连接)");
        clientBtn.addActionListener(e -> connectByInvite());

        JButton copyBtn = new JButton("复制邀请码");
        copyBtn.addActionListener(e -> {
            String text = inviteOut.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new java.awt.datatransfer.StringSelection(text), null);
            log.println("邀请码已复制到剪贴板");
        });

        inviteOut.setLineWrap(true);
        inviteOut.setWrapStyleWord(true);

        add(new JLabel("棋类选择"));
        add(gameSel);
        add(serverBtn);
        add(new JLabel("邀请码输出"));
        add(new JScrollPane(inviteOut));
        add(copyBtn);
        add(new JLabel("输入邀请码连接"));
        add(inviteIn);
        add(clientBtn);
    }

    public void setBoard(BoardCanvas board){
        this.board = board;
    }

    public MoveSender getCurrentSender(){
        return currentSender;
    }

    private void startServerAndGenInvite(){
        try {
            int port = 2266;
            String ip = fetchPublicIP();
            String code = InviteCodec.gen(ip, port);
            inviteOut.setText(code);
            log.println("公网IP " + ip + " 端口 " + port + " 已生成邀请码");

            // Start server and bind board as listener
            server = new NetServer(port, log, (x,y) -> {
                if (board != null) board.onOpponentMove(x,y);
            });
            server.start();
            // Sender is server (server sends to client)
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
            client.connect(inviteIn.getText().trim());
            log.println("连接已建立");
            currentSender = client;
        } catch (Exception ex){
            log.println("连接失败: " + ex.getMessage());
        }
    }

    private static String fetchPublicIP() throws Exception {
        URL url = new URL("http://checkip.amazonaws.com/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (Scanner sc = new Scanner(conn.getInputStream())) {
            return sc.nextLine().trim();
        }
    }
}

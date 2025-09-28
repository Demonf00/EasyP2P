package com.easy.ui;

import javax.swing.*;
import com.easy.game.*;
import com.easy.net.*;
import java.awt.*;

public class MainFrame extends JFrame implements ConsoleSink {
    private JComponent selector;
    private void switchSelector(boolean isHost){
        Container parent = selector.getParent();
        if (parent != null) parent.remove(selector);
        selector = new GameSelectorPanel(isHost, this::hostSelectGame, this::clientSuggestGame);
        ((JPanel)((BorderLayout)((Container)parent).getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER)).add(selector, java.awt.BorderLayout.EAST);
        // Rebuild layout
        this.revalidate(); this.repaint();
        println(isHost ? "你是房主：可以选择游戏" : "你是客户端：可以建议游戏");
        // 记录我方是host还是client
        board.setHost(isHost);
    }
    private final ConsolePanel console = new ConsolePanel();
    private final SidebarPanel sidebar = new SidebarPanel(this);
    private BoardCanvas board;

    public MainFrame(){
        super("easy-p2p 对战");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        // board needs current MoveSender
        board = 
new BoardCanvas(new com.easy.ui.MoveSender(){
    @Override public void sendMove(int x,int y,int turn,String hash) throws java.io.IOException {
        MoveSender sender = sidebar.getCurrentSender();
        if (sender == null) throw new java.io.IOException("尚未建立连接");
        sender.sendMove(x,y,turn,hash);
    }
}, this);

        sidebar.setBoard(board);
        sidebar.onRoleKnown(isHost -> switchSelector(isHost));

        add(sidebar, BorderLayout.WEST);
        add(board, BorderLayout.CENTER);
        add(console, BorderLayout.SOUTH);
        setSize(1100, 780);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    @Override
    public void println(String s){
        console.println(s);
    }

    // Called when host selects a game from selector
    private void hostSelectGame(GameType type){
        println("房主选择游戏：" + type);
        MoveSender sender = sidebar.getCurrentSender();
        if (sender instanceof NetServer) {
            try {
                java.io.OutputStream out = ((NetServer)sender).getClass().getDeclaredField("out")!=null ? null : null;
            } catch (Exception ignore){}
        }
        // Send a GAME select over whichever side we're on
        try {
            com.easy.net.NetServer s = getServerIfAny();
            com.easy.net.NetClient c = getClientIfAny();
            String starter = "host";
            if (s != null) {
                // I'm host: send to client
                s.sendJson(com.easy.net.Proto.gameSelect(type.name(), starter));
            } else if (c != null) {
                // If I'm client but UI仍显示host选择，忽略
                println("当前非房主，无法选择，只能建议。");
            }
        } catch (Exception ex) {
            println("发送游戏选择失败: " + ex.getMessage());
        }
        // Apply locally too
        board.onGameSelected(type, "host");
    }

    private void clientSuggestGame(GameType type){
        println("客户端建议游戏：" + type);
        try {
            com.easy.net.NetClient c = getClientIfAny();
            if (c != null) {
                c.sendJson(com.easy.net.Proto.gameSuggest(type.name()));
            }
        } catch (Exception ex){
            println("发送建议失败: " + ex.getMessage());
        }
    }

    private com.easy.net.NetServer getServerIfAny(){
        try {
            java.lang.reflect.Field f = SidebarPanel.class.getDeclaredField("server");
            f.setAccessible(true);
            return (com.easy.net.NetServer) f.get(sidebar);
        } catch (Exception e){ return null; }
    }
    private com.easy.net.NetClient getClientIfAny(){
        try {
            java.lang.reflect.Field f = SidebarPanel.class.getDeclaredField("client");
            f.setAccessible(true);
            return (com.easy.net.NetClient) f.get(sidebar);
        } catch (Exception e){ return null; }
    }
}

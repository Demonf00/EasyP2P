package com.easy.ui;

import javax.swing.*;
import java.awt.*;
import com.easy.game.*;
import com.easy.net.*;

public class MainFrame extends JFrame implements ConsoleSink {
    private JPanel top;
    private JComponent selector;

    private final ConsolePanel console = new ConsolePanel();
    private final SidebarPanel sidebar = new SidebarPanel(this);
    private BoardCanvas board;

    // 全局：下一局由谁先（Host/Client 每局翻转）
    private boolean hostStartsNext = true;

    public MainFrame(){
        super("easy-p2p 对战");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        board = new BoardCanvas(new MoveSender(){
            @Override public void sendMove(int x,int y,int turn,String hash) throws java.io.IOException {
                MoveSender real = sidebar.getCurrentSender();
                if (real==null) throw new java.io.IOException("尚未建立连接");
                real.sendMove(x,y,turn,hash);
            }
        }, this);

        sidebar.setBoard(board);
        sidebar.onRoleKnown(this::switchSelector);

        top = new JPanel(new BorderLayout(8,8));
        add(sidebar, BorderLayout.WEST);
        add(board,   BorderLayout.CENTER);
        add(console, BorderLayout.SOUTH);

        setSize(1100, 780);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void switchSelector(boolean isHost){
        if (selector != null) remove(selector);
        selector = new GameSelectorPanel(isHost, this::hostSelectGame, this::clientSuggestGame);
        add(selector, BorderLayout.EAST);

        revalidate();
        repaint();

        println(isHost ? "你是房主：可以选择游戏" : "你是客户端：可以建议游戏");
        board.setHost(isHost);
        board.setHostStartsNext(hostStartsNext);
    }

    // Host 选择 -> 发送 select，并立即本地切盘
    private void hostSelectGame(GameType type){
        println("房主选择游戏：" + type);
        try {
            var s = getServerIfAny();
            if (s!=null) s.sendJson(Proto.gameSelect(type.name(), "host"));
        } catch (Exception ex){ println("发送游戏选择失败: " + ex.getMessage()); }
        board.setHostStartsNext(hostStartsNext);
        board.manualReset(hostStartsNext); // 切盘时按全局交替
        board.setGame(type, board.currentType()==type ? (hostStartsNext==sidebarIsHost()) : (hostStartsNext==sidebarIsHost()));
    }

    private void clientSuggestGame(GameType type){
        println("客户端建议游戏：" + type);
        try {
            var c = getClientIfAny();
            if (c!=null) c.sendJson(Proto.gameSuggest(type.name()));
        } catch (Exception ex){ println("发送建议失败: " + ex.getMessage()); }
    }

    private boolean sidebarIsHost(){
        try {
            var f = SidebarPanel.class.getDeclaredField("server");
            f.setAccessible(true);
            return f.get(sidebar)!=null;
        } catch (Exception e){ return false; }
    }

    private NetServer getServerIfAny(){
        try {
            var f = SidebarPanel.class.getDeclaredField("server");
            f.setAccessible(true);
            return (NetServer) f.get(sidebar);
        } catch (Exception e){ return null; }
    }
    private NetClient getClientIfAny(){
        try {
            var f = SidebarPanel.class.getDeclaredField("client");
            f.setAccessible(true);
            return (NetClient) f.get(sidebar);
        } catch (Exception e){ return null; }
    }

    @Override public void println(String s){ console.println(s); }
}

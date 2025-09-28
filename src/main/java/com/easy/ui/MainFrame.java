package com.easy.ui;

import javax.swing.*;
import java.awt.*;

import com.easy.game.GameType;
import com.easy.ui.MoveSender;

import com.easy.ui.GameStartPolicy;

import com.easy.net.NetClient;
import com.easy.net.NetServer;
import com.easy.net.Proto;

public class MainFrame extends JFrame implements ConsoleSink {
    // 顶部区域：左侧侧栏 + 中间棋盘 + 右侧选择面板
    private JPanel top;
    private JComponent selector;

    private final ConsolePanel  console = new ConsolePanel();
    private final SidebarPanel  sidebar = new SidebarPanel(this);
    private BoardCanvas         board;

    public MainFrame() {
        super("easy-p2p 对战");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // 棋盘需要在发送时拿到当前的连接端（server/client）
        board = new BoardCanvas(new MoveSender() {
            @Override public void sendMove(int x, int y, int turn, String hash) throws java.io.IOException {
                MoveSender sender = sidebar.getCurrentSender();
                if (sender == null) throw new java.io.IOException("尚未建立连接");
                sender.sendMove(x, y, turn, hash);
            }
        }, this);

        // 侧栏知道棋盘，角色确定后会回调 switchSelector
        sidebar.setBoard(board);
        sidebar.onRoleKnown(this::switchSelector);

        // ===== 上半部：左侧侧栏 + 中间棋盘 + 右侧选择 =====
        top = new JPanel(new BorderLayout(8, 8));
        top.add(sidebar, BorderLayout.WEST);
        top.add(board,   BorderLayout.CENTER);

        // 先放“客户端视图”的选择面板；建立连接后根据角色切换
        selector = new GameSelectorPanel(false, this::hostSelectGame, this::clientSuggestGame);
        top.add(selector, BorderLayout.EAST);

        // ===== 下半部：控制台 =====
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, console);
        split.setResizeWeight(1.0);     // 绝大部分空间给上半部
        split.setDividerSize(6);
        add(split, BorderLayout.CENTER);

        setSize(1100, 780);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // 切换右侧选择面板（房主=可选择；客户端=可建议）
    private void switchSelector(boolean isHost) {
        if (selector != null) {
            top.remove(selector);
        }
        selector = new GameSelectorPanel(isHost, this::hostSelectGame, this::clientSuggestGame);
        top.add(selector, BorderLayout.EAST);

        revalidate();
        repaint();

        println(isHost ? "你是房主：可以选择游戏" : "你是客户端：可以建议游戏");
        board.setHost(isHost);
    }

    @Override
    public void println(String s) {
        console.println(s);
    }

    // ===== 右侧按钮回调 =====

    // 房主点击右侧按钮 -> 选择游戏并广播给客户端
    private void hostSelectGame(GameType type) {
        println("房主选择游戏：" + type);

        // 1) 从全局策略拿本局先手
        boolean thisRoundHostStart = GameStartPolicy.nextStartIsHost();

        // 2) 先本地应用（EDT），再广播
        javax.swing.SwingUtilities.invokeLater(() -> {
            board.onGameSelected(type, thisRoundHostStart ? "host" : "client");
        });

        // 3) 发给对端
        try {
            com.easy.net.NetServer s = getServerIfAny();
            if (s != null) {
                s.sendJson(com.easy.net.Proto.gameSelect(type.name(),
                        thisRoundHostStart ? "host" : "client"));
            } else {
                println("当前不是房主，无法选择，只能建议。");
                return;
            }
        } catch (Exception ex) {
            println("发送游戏选择失败: " + ex.getMessage());
            return;
        }

        // 4) 开始一局 -> 消耗并翻转先手到下一局
        GameStartPolicy.consumeAndFlip();
    }

    // 客户端点击右侧按钮 -> 仅发送建议
    private void clientSuggestGame(GameType type) {
        println("客户端建议游戏：" + type);
        try {
            NetClient c = getClientIfAny();
            if (c != null) {
                c.sendJson(Proto.gameSuggest(type.name()));
            }
        } catch (Exception ex) {
            println("发送建议失败: " + ex.getMessage());
        }
    }

    // ===== 小工具：从侧栏取出现有的 server/client =====
    private NetServer getServerIfAny() {
        try {
            var f = SidebarPanel.class.getDeclaredField("server");
            f.setAccessible(true);
            return (NetServer) f.get(sidebar);
        } catch (Exception e) { return null; }
    }
    private NetClient getClientIfAny() {
        try {
            var f = SidebarPanel.class.getDeclaredField("client");
            f.setAccessible(true);
            return (NetClient) f.get(sidebar);
        } catch (Exception e) { return null; }
    }
}

package com.easy.ui;

import com.easy.game.GameType;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class GameSelectorPanel extends JPanel {
    private final boolean isHost;
    public GameSelectorPanel(boolean isHost, Consumer<GameType> onSelect, Consumer<GameType> onSuggest){
        this.isHost = isHost;
        setLayout(new GridLayout(0,1,6,6));
        add(new JLabel(isHost ? "房主可选择游戏：" : "客户端可建议游戏："));
        add(makeBtn("五子棋 (15x15)", GameType.GOMOKU, onSelect, onSuggest));
        add(makeBtn("黑白棋 / Reversi (8x8)", GameType.REVERSI, onSelect, onSuggest));
    }
    private JComponent makeBtn(String text, GameType gt, Consumer<GameType> onSelect, Consumer<GameType> onSuggest){
        JButton b = new JButton(text);
        b.setPreferredSize(new Dimension(160, 28));
        if (isHost) {
            b.addActionListener(e -> onSelect.accept(gt));
        } else {
            b.addActionListener(e -> onSuggest.accept(gt));
            b.setEnabled(true); // 可点但仅发送建议
        }
        return b;
    }
}

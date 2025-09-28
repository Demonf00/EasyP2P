package com.easy.ui;

import com.easy.game.GameType;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class GameSelectorPanel extends JPanel {
    private final boolean isHost;
    private final Consumer<GameType> onHostSelect;
    private final Consumer<GameType> onClientSuggest;

    public GameSelectorPanel(boolean isHost,
                             Consumer<GameType> onHostSelect,
                             Consumer<GameType> onClientSuggest) {
        this.isHost = isHost;
        this.onHostSelect = onHostSelect;
        this.onClientSuggest = onClientSuggest;

        setLayout(new GridLayout(0,1,6,6));
        setBorder(BorderFactory.createTitledBorder(isHost ? "选择游戏(房主)" : "建议游戏(客户端)"));

        add(makeBtn("五子棋",   GameType.GOMOKU));
        add(makeBtn("黑白棋",   GameType.REVERSI));
        add(makeBtn("西洋跳棋", GameType.CHECKERS));
        add(makeBtn("西洋棋",   GameType.CHESS));
        add(makeBtn("海战棋",   GameType.BATTLE));
    }

    private JButton makeBtn(String label, GameType type){
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            if (isHost) onHostSelect.accept(type);
            else onClientSuggest.accept(type);
        });
        return b;
    }
}

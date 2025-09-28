package com.easy.ui;

import javax.swing.*;
import java.awt.*;
import com.easy.game.GameType;
import com.easy.net.Proto;

public class GameSelectorPanel extends JPanel {

    private final java.util.function.Consumer<GameType> hostSelect;
    private final java.util.function.Consumer<GameType> clientSuggest;

    public GameSelectorPanel(boolean isHost,
                             java.util.function.Consumer<GameType> hostSelect,
                             java.util.function.Consumer<GameType> clientSuggest){
        this.hostSelect = hostSelect;
        this.clientSuggest = clientSuggest;

        setLayout(new GridLayout(0,1,6,6));
        add(btn("五子棋", GameType.GOMOKU, isHost));
        add(btn("黑白棋", GameType.REVERSI, isHost));
        add(btn("西洋跳棋", GameType.CHECKERS, isHost));
        add(btn("西洋棋", GameType.CHESS, isHost));
        add(btn("海战棋", GameType.BATTLE, isHost));
    }

    private JButton btn(String text, GameType t, boolean isHost){
        JButton b = new JButton(text);
        b.addActionListener(e -> {
            if (isHost) hostSelect.accept(t);
            else clientSuggest.accept(t);
        });
        return b;
    }
}

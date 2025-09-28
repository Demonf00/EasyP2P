package com.easy.ui;
import com.easy.game.GameType;
public interface NetEventListener {
    void onOpponentMove(int x, int y);
    void onGameSelected(GameType type, String starter);
    void onGameSuggested(GameType type);
}

package com.easy.game;

public interface Game {
    int size();                 // board size (n x n)
    int currentPlayer();        // 1 black/self, 2 white/opponent
    boolean myTurn();           // is it my turn
    void setMyTurn(boolean my);
    boolean play(int x, int y); // try make a move; return true if applied
    int get(int x, int y);      // 0 empty, 1 black, 2 white
    boolean isFinished();       // finished?
    String resultText();        // winner text or end reason
    void reset(boolean hostStarts); // reset and set starter
    GameType type();
}

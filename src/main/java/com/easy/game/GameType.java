package com.easy.game;
public enum GameType {
    GOMOKU, REVERSI;
    public static GameType from(String s){
        if (s == null) return GOMOKU;
        s = s.trim().toUpperCase();
        return "REVERSI".equals(s) ? REVERSI : GOMOKU;
    }
}

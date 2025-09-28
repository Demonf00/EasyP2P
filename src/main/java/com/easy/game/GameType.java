package com.easy.game;

import java.util.Locale;

public enum GameType {
    GOMOKU, 
    REVERSI, 
    CHECKERS, 
    BATTLE, 
    CHESS;
    public static GameType from(String s) {
        if (s == null) return GOMOKU;
        String t = s.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "GOMOKU":     return GOMOKU;
            case "REVERSI":    return REVERSI;
            case "CHECKERS":   return CHECKERS;
            case "CHESS":      return CHESS;
            case "BATTLE":
            case "BATTLESHIP":
            case "NAVY":
            case "SEA_BATTLE": return BATTLE;
            default:           return GOMOKU; // 兜底
        }
    }
}

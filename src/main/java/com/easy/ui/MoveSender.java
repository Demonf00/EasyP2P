package com.easy.ui;
import java.io.IOException;

public interface MoveSender {
    void sendMove(int x, int y, int turn, String hash) throws IOException;
}

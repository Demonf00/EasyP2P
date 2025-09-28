package com.easy.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class BoardCanvas extends JPanel implements MoveListener {
    private final int n = 15;
    private final int cell = 36;
    private final int[][] board = new int[n][n]; // 0 empty, 1 self, 2 opponent
    private boolean myTurn = true;
    private int turn = 1;
    private final MoveSender sender;
    private final ConsoleSink log;

    public BoardCanvas(MoveSender sender, ConsoleSink log){
        this.sender = sender; this.log = log;
        setPreferredSize(new Dimension(n * cell, n * cell));
        addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                int x = e.getX() / cell;
                int y = e.getY() / cell;
                if (!myTurn || x<0 || x>=n || y<0 || y>=n || board[y][x]!=0) return;
                board[y][x] = 1;
                myTurn = false; turn++;
                repaint();
                try {
                    sender.sendMove(x, y, turn, hash());
                } catch (Exception ex){
                    log.println("发送落子失败: " + ex.getMessage());
                }
            }
        });
    }

    private String hash(){
        return Integer.toHexString(java.util.Arrays.deepHashCode(board));
    }

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        g.setColor(Color.LIGHT_GRAY);
        for (int i=0;i<=n;i++){
            g.drawLine(0, i*cell, n*cell, i*cell);
            g.drawLine(i*cell, 0, i*cell, n*cell);
        }
        for (int y=0;y<n;y++) for (int x=0;x<n;x++){
            if (board[y][x]==0) continue;
            g.setColor(board[y][x]==1? Color.BLACK : Color.WHITE);
            g.fillOval(x*cell+4, y*cell+4, cell-8, cell-8);
        }
    }

    @Override
    public void onOpponentMove(int x, int y){
        if (x<0 || x>=n || y<0 || y>=n || board[y][x]!=0) return;
        board[y][x] = 2;
        myTurn = true;
        turn++;
        repaint();
    }
}

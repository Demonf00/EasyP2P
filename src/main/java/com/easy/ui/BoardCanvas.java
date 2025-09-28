package com.easy.ui;

import com.easy.game.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class BoardCanvas extends JPanel implements MoveListener, NetEventListener {
    private int round = 1;
    private Game game;
    private final MoveSender sender;
    private final ConsoleSink log;
    private boolean hostSide = true;     // true: I'm host; false: I'm client
    private boolean hostStarts = true;   // alternates each round

    public BoardCanvas(MoveSender sender, ConsoleSink log){
        this.sender = sender; this.log = log;
        setPreferredSize(new Dimension(560, 560));
        setGame(GameType.GOMOKU, true); // default

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (game == null) return;

                int n = game.size();
                int cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n));
                int w = cell * n, h = cell * n;
                int x0 = (getWidth()  - w) / 2;
                int y0 = (getHeight() - h) / 2;

                // 把鼠标坐标映射到棋盘坐标系
                int px = e.getX() - x0;
                int py = e.getY() - y0;

                // 点击在棋盘之外则忽略
                if (px < 0 || py < 0 || px >= w || py >= h) return;

                // 转为格子索引（0..n-1）
                int x = px / cell;
                int y = py / cell;

                if (!game.myTurn()) { 
                    log.println("现在不是你的回合"); 
                    return; 
                }

                if (game.play(x, y)) {
                    repaint();
                    try {
                        sender.sendMove(x, y, 0, "");
                    } catch (Exception ex) {
                        log.println("发送落子失败: " + ex.getMessage());
                    }
                    afterMoveCheck();
                } else {
                    log.println("此处不可落子");
                }
            }
        });
    }

    public void setHost(boolean isHost){ this.hostSide = isHost; }

    private void afterMoveCheck(){
        // no-op
        if (game.isFinished()){
            log.println("游戏结束：" + game.resultText() + "，3秒后重开下一局（先后手互换）");
            // flip starter
            hostStarts = !hostStarts;
            javax.swing.Timer t = new javax.swing.Timer(3000, e-> {
                setGame(game.type(), hostStarts == hostSide); // hostStarts true means host starts; compare to my side
                repaint();
            });
            t.setRepeats(false); t.start();
        }
    }

    public void setGame(GameType type, boolean iStart){
        if (type == GameType.REVERSI) game = new ReversiGame();
        else game = new GomokuGame();
        // iStart: whether I start; host alternates; map to game.myTurn
        game.reset(iStart);
        log.println("游戏开始：" + type + "，" + (iStart ? "你先手" : "你后手"));
        repaint();
    }

    @Override
    public void onOpponentMove(int x, int y){
        if (game == null) return;
        // Apply move for opponent: simply call play since turns alternate
        game.play(x,y);
        repaint();
        afterMoveCheck();
    }

    // Net events
    @Override
    public void onGameSelected(GameType type, String starter){
        boolean hostStart = "host".equalsIgnoreCase(starter);
        boolean iStart = hostStart == hostSide;
        setGame(type, iStart);
    }
    @Override
    public void onGameSuggested(GameType type){
        log.println("对方建议切换游戏：" + type);
    }

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        if (game == null) return;
        int n = game.size();
        int cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n));
        int w = cell * n, h = cell * n;
        int x0 = (getWidth()-w)/2, y0 = (getHeight()-h)/2;

        // grid/background
        g.setColor(new Color(240, 230, 200));
        g.fillRect(x0, y0, w, h);
        g.setColor(Color.GRAY);
        for (int i=0;i<=n;i++){
            g.drawLine(x0, y0+i*cell, x0+w, y0+i*cell);
            g.drawLine(x0+i*cell, y0, x0+i*cell, y0+h);
        }

        // pieces
        for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
            int v = game.get(xx, yy);
            if (v==0) continue;
            g.setColor(v==1? Color.BLACK : Color.WHITE);
            g.fillOval(x0+xx*cell+4, y0+yy*cell+4, cell-8, cell-8);
            g.setColor(Color.DARK_GRAY);
            g.drawOval(x0+xx*cell+4, y0+yy*cell+4, cell-8, cell-8);
        }

        // status
        g.setColor(Color.DARK_GRAY);
        g.drawString("当前：" + (game.currentPlayer()==1? "黑":"白") + (game.myTurn()?" (你的回合)":" (对方回合)") + " | " + game.resultText(), x0, y0+h+16);
    }
    public void manualReset(boolean hostStartsNow){
        // Reset current game; hostStartsNow true -> host先手
        boolean iStart = hostStartsNow == hostSide;
        setGame(game.type(), iStart);
        round = 1;
    }
    public com.easy.game.GameType currentType(){ return game.type(); }
}

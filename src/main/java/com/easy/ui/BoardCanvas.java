package com.easy.ui;

import javax.swing.*;
import com.easy.game.*;
import com.easy.net.Proto;

import com.easy.game.BattleshipGame;
import com.easy.game.CheckersGame;
import com.easy.game.ChessGame;

import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class BoardCanvas extends JPanel implements MoveListener, NetEventListener {
    private Game game;
    private final MoveSender sender;
    private final ConsoleSink log;

    private boolean hostSide = true;     // 我是否是房主
    private boolean hostStarts = true;   // 每局谁先（房主与客户端轮换）

    // 最近一步高亮
    private Point lastMine = null;
    private Point lastOpp  = null;

    // 棋种相关的选择（用于 Checkers/Chess 等需要“起点->终点”的游戏）
    private Point sel = null; // 当前选中的格子（仅本地操控）

    public BoardCanvas(MoveSender sender, ConsoleSink log){
        this.sender = sender; this.log = log;
        setPreferredSize(new Dimension(560, 560));
        setGame(GameType.GOMOKU, true); // 默认

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (game == null) return;
                int n = game.size();
                int cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n));
                int w = cell * n, h = cell * n;
                int x0 = (getWidth()  - w) / 2;
                int y0 = (getHeight() - h) / 2;

                int px = e.getX() - x0;
                int py = e.getY() - y0;
                if (px < 0 || py < 0 || px >= w || py >= h) return;

                int x = px / cell;
                int y = py / cell;

                if (game.type() == GameType.CHESS) {
                    onClickChess(x,y);
                    return;
                } else if (game.type() == GameType.CHECKERS) {
                    onClickCheckers(x,y);
                    return;
                } else if (game.type() == GameType.BATTLE) {
                    onClickBattle(x,y, px,py, cell,x0,y0); // 你之前的海战棋交互（左盘布阵/右盘开火）
                    return;
                }

                // 其他棋（Gomoku/Reversi）：单点落子
                if (!game.myTurn()) {
                    log.println("现在不是你的回合");
                    return;
                }
                if (game.play(x, y)) {
                    lastMine = new Point(x,y);
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
        if (game.isFinished()){
            log.println("游戏结束：" + game.resultText() + "，3秒后重开下一局（先后手互换）");
            hostStarts = !hostStarts;
            javax.swing.Timer t = new javax.swing.Timer(3000, e-> {
                setGame(game.type(), hostStarts == hostSide);
                lastMine = lastOpp = sel = null;
                repaint();
            });
            t.setRepeats(false); t.start();
        }
    }

    public void setGame(GameType type, boolean iStart){
        if (type == GameType.REVERSI) game = new ReversiGame();
        else if (type == GameType.CHECKERS) game = new CheckersGame();
        else if (type == GameType.CHESS) game = new ChessGame();
        else if (type == GameType.BATTLE) game = new BattleshipGame();
        else game = new GomokuGame();

        game.reset(iStart);
        try { game.setMyTurn(iStart); } catch (Throwable ignore){}

        log.println("游戏开始：" + type + "，" + (iStart ? "你先手" : "你后手"));
        repaint();
    }

    // === 处理对方走子 ===

    @Override
    public void onOpponentMove(int x, int y){
        // 普通棋（无起点）
        if (game == null) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (game.play(x,y)){
                lastOpp = new java.awt.Point(x,y);
                repaint();
                afterMoveCheck();
            }
        });
    }

    /** 新增：带起点的对方走子（用于 Chess/Checkers 等） */
    public void onOpponentMoveFxFy(int fx,int fy,int x,int y){
        if (game == null) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            switch (game.type()){
                case CHESS -> {
                    com.easy.game.ChessGame cg = (com.easy.game.ChessGame) game;
                    if (cg.moveFromPeer(fx,fy,x,y)) {
                        lastOpp = new java.awt.Point(x,y);
                        repaint();
                        afterMoveCheck();
                    }
                }
                case CHECKERS -> {
                    com.easy.game.CheckersGame ck = (com.easy.game.CheckersGame) game;
                    if (ck.moveFromPeer(fx,fy,x,y)) {
                        lastOpp = new java.awt.Point(x,y);
                        repaint();
                        afterMoveCheck();
                    }
                }
                default -> {
                    if (game.play(x,y)) {
                        lastOpp = new java.awt.Point(x,y);
                        repaint(); afterMoveCheck();
                    }
                }
            }
        });
    }

    // === 本地交互：Chess/Checkers/Battle ===

    private void onClickChess(int x,int y){
        ChessGame cg = (ChessGame) game;
        int v = cg.get(x,y);

        // 没选中 -> 选自己子
        if (sel == null) {
            if (v==0 || !cg.isMyPiece(v)) {
                log.println("请选择你方棋子");
                return;
            }
            sel = new Point(x,y);
            repaint();
            return;
        }

        // 已有选中：如果再次点自己子 -> 改变选中
        if (v!=0 && cg.isMyPiece(v)) {
            sel = new Point(x,y);
            repaint();
            return;
        }

        // 尝试 from->to
        int fx = sel.x, fy = sel.y;
        if (!game.myTurn()){
            log.println("现在不是你的回合"); return;
        }
        if (cg.move(fx,fy,x,y)) {
            lastMine = new Point(x,y);
            sel = null;
            repaint();
            try {
                // 发起点+终点
                sender.sendMove(x, y, 0, ""); // 兼容旧接口
                // 发送带 fx/fy 的 MOVE
                if (sender instanceof com.easy.net.NetServer) {
                    ((com.easy.net.NetServer) sender).sendJson(Proto.moveFxFy(fx,fy,x,y));
                } else if (sender instanceof com.easy.net.NetClient) {
                    ((com.easy.net.NetClient) sender).sendJson(Proto.moveFxFy(fx,fy,x,y));
                }
            } catch (Exception ex) {
                log.println("发送走子失败: " + ex.getMessage());
            }
            afterMoveCheck();
        } else {
            log.println("非法走子");
        }
    }

    private void onClickCheckers(int x,int y){
        // 如果你的 Checkers 是“先选子再落点”，可参照 Chess 的模式；此处留简化示意
        if (!game.myTurn()) { log.println("现在不是你的回合"); return; }
        if (game.play(x,y)) {
            lastMine = new Point(x,y);
            repaint();
            try { sender.sendMove(x,y,0,""); } catch (Exception ex){ log.println("发送失败: " + ex.getMessage()); }
            afterMoveCheck();
        } else {
            log.println("此处不可落子");
        }
    }

    private void onClickBattle(int x,int y,int px,int py,int cell,int x0,int y0){
        // 保持你现有海战棋逻辑：布阵/开火……这里不展开
        // 请在对应的成功“开火/命中/落空”时设置 lastMine/lastOpp 对应点位以高亮
    }

    // === NetEventListener ===
    @Override
    public void onGameSelected(com.easy.game.GameType type, String starter){
        boolean hostStart = "host".equalsIgnoreCase(starter);
        boolean iStart = hostStart == hostSide;
        javax.swing.SwingUtilities.invokeLater(() -> setGame(type, iStart));
    }
    @Override
    public void onGameSuggested(GameType type){
        log.println("对方建议切换游戏：" + type);
    }

    // === 绘制 ===
    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        if (game == null) return;

        int n = game.size();
        int cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n));
        int w = cell * n, h = cell * n;
        int x0 = (getWidth()-w)/2, y0 = (getHeight()-h)/2;

        // 背景 & 网格
        g.setColor(new Color(240, 230, 200));
        g.fillRect(x0, y0, w, h);
        g.setColor(Color.GRAY);
        for (int i=0;i<=n;i++){
            g.drawLine(x0, y0+i*cell, x0+w, y0+i*cell);
            g.drawLine(x0+i*cell, y0, x0+i*cell, y0+h);
        }

        // 各棋种绘制
        if (game.type()==GameType.GOMOKU || game.type()==GameType.REVERSI || game.type()==GameType.CHECKERS){
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                int v = game.get(xx, yy);
                if (v==0) continue;
                g.setColor(v==1? Color.BLACK : Color.WHITE);
                g.fillOval(x0+xx*cell+4, y0+yy*cell+4, cell-8, cell-8);
                g.setColor(Color.DARK_GRAY);
                g.drawOval(x0+xx*cell+4, y0+yy*cell+4, cell-8, cell-8);
            }
        } else if (game.type()==GameType.CHESS) {
            // 画棋盘黑白格
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                if ( ((xx+yy)&1)==1 ){
                    g.setColor(new Color(181, 136, 99));
                    g.fillRect(x0+xx*cell, y0+yy*cell, cell, cell);
                } else {
                    g.setColor(new Color(240, 217, 181));
                    g.fillRect(x0+xx*cell, y0+yy*cell, cell, cell);
                }
            }
            // 选中格提示
            if (sel != null){
                g.setColor(new Color(0,120,215,80));
                g.fillRect(x0+sel.x*cell, y0+sel.y*cell, cell, cell);
                g.setColor(new Color(0,120,215));
                g.drawRect(x0+sel.x*cell, y0+sel.y*cell, cell, cell);
            }
            // 画棋子（用 Unicode 符号）
            ChessGame cg = (ChessGame) game;
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                int v = cg.get(xx, yy);
                if (v==0) continue;
                String sym = chessSymbol(v);
                g.setColor(v>0 ? Color.BLACK : Color.BLACK);
                g.setFont(getFont().deriveFont(Font.PLAIN, cell*0.7f));
                FontMetrics fm = g.getFontMetrics();
                int sx = x0+xx*cell + (cell - fm.stringWidth(sym))/2;
                int sy = y0+yy*cell + (cell + fm.getAscent()-fm.getDescent())/2 - 2;
                g.drawString(sym, sx, sy);
            }
        } else if (game.type()==GameType.BATTLE) {
            // 你的海战棋双盘绘制逻辑保持；请在成功“发射/命中/落空”时设置 lastMine/lastOpp
        }

        // 最新一步高亮
        if (lastMine != null){
            g.setColor(new Color(46, 204, 113, 160)); // 绿
            g.fillRect(x0+lastMine.x*cell, y0+lastMine.y*cell, cell, cell);
        }
        if (lastOpp != null){
            g.setColor(new Color(231, 76, 60, 150)); // 红
            g.drawRect(x0+lastOpp.x*cell, y0+lastOpp.y*cell, cell-1, cell-1);
            g.drawRect(x0+lastOpp.x*cell+1, y0+lastOpp.y*cell+1, cell-3, cell-3);
        }

        // 状态行
        g.setColor(Color.DARK_GRAY);
        String info = "当前：" + (game.currentPlayer()==1? "黑/白先" : "白/黑先"); // 这里仅占位
        g.drawString(info + " | " + game.resultText(), x0, y0+h+16);
    }

    private String chessSymbol(int v){
        // 白子(>0)：♙♘♗♖♕♔   黑子(<0)：♟♞♝♜♛♚
        int a = Math.abs(v);
        boolean white = v>0;
        switch (a){
            case ChessGame.WP: return white ? "♙" : "♟";
            case ChessGame.WN: return white ? "♘" : "♞";
            case ChessGame.WB: return white ? "♗" : "♝";
            case ChessGame.WR: return white ? "♖" : "♜";
            case ChessGame.WQ: return white ? "♕" : "♛";
            case ChessGame.WK: return white ? "♔" : "♚";
        }
        return "?";
    }

    public void manualReset(boolean hostStartsNow){
        boolean iStart = hostStartsNow == hostSide;
        setGame(game.type(), iStart);
    }

    public com.easy.game.GameType currentType(){ return game.type(); }
}

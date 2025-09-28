package com.easy.ui;

import javax.swing.*;
import com.easy.game.*;
import com.easy.net.Proto;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class BoardCanvas extends JPanel implements MoveListener, NetEventListener {
    private Game game;
    private final MoveSender sender;
    private final ConsoleSink log;

    private boolean hostSide = true;     // 我是否是房主
    private boolean hostStarts = true;   // 每局谁先（房主与客户端轮换）

    // —— 视图翻转（Chess/Checkers 先手端本方在下）——
    private boolean flip = false;
    private int toView (int m){ return flip ? (game.size()-1 - m) : m; }
    private int toModel(int v){ return flip ? (game.size()-1 - v) : v; }

    // 最近一步高亮（存模型坐标）
    private Point lastMine = null;
    private Point lastOpp  = null;

    // 选中/提示（Chess/Checkers）
    private Point sel = null;                     // 模型坐标
    private List<Point> moveHints = new ArrayList<>(); // 模型坐标

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

                // 视图格坐标 -> 模型格坐标（考虑 flip）
                int vx = px / cell, vy = py / cell;
                int x = toModel(vx), y = toModel(vy);

                switch (game.type()){
                    case CHESS     -> onClickChess(x,y);
                    case CHECKERS  -> onClickCheckers(x,y);
                    case BATTLE    -> onClickBattle(vx,vy, px,py, cell,x0,y0);
                    default        -> onClickPointGames(x,y);
                }
            }
        });
    }

    public void setHost(boolean isHost){ this.hostSide = isHost; }

    // —— 清空覆盖层（切盘/重置时都要调用）——
    private void clearOverlays(){
        sel = null;
        moveHints.clear();
        lastMine = null;
        lastOpp  = null;
        repaint();
    }

    private void afterMoveCheck(){
        if (game.isFinished()){
            log.println("游戏结束：" + game.resultText() + "，3秒后重开下一局（先后手互换）");
            hostStarts = !hostStarts;
            javax.swing.Timer t = new javax.swing.Timer(3000, e-> {
                setGame(game.type(), hostStarts == hostSide);
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

        // 视角：先手端本方在下 => 只有当我不是先手时翻转
        switch (type){
            case CHESS, CHECKERS -> flip = !iStart;
            default              -> flip = false;
        }
        clearOverlays();

        log.println("游戏开始：" + type + "，" + (iStart ? "你先手" : "你后手"));
        repaint();
    }

    // === 处理对方走子 ===

    @Override
    public void onOpponentMove(int x, int y){
        if (game == null) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            // 无起点类（Gomoku/Reversi 等）
            if (game.play(x,y)){
                lastOpp = new Point(x,y);
                repaint();
                afterMoveCheck();
            }
        });
    }

    /** 带起点（Chess/Checkers） */
    public void onOpponentMoveFxFy(int fx,int fy,int x,int y){
        if (game == null) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            switch (game.type()){
                case CHESS -> {
                    ChessGame cg = (ChessGame) game;
                    if (cg.moveFromPeer(fx,fy,x,y)) {
                        lastOpp = new Point(x,y);
                        repaint(); afterMoveCheck();
                    }
                }
                case CHECKERS -> {
                    CheckersGame ck = (CheckersGame) game;
                    if (ck.moveFromPeer(fx,fy,x,y)) {
                        lastOpp = new Point(x,y);
                        repaint(); afterMoveCheck();
                    }
                }
                default -> { /* ignore */ }
            }
        });
    }

    // === 本地交互 ===

    private void onClickPointGames(int x,int y){
        if (!game.myTurn()) { log.println("现在不是你的回合"); return; }
        if (game.play(x, y)) {
            lastMine = new Point(x,y);
            repaint();
            try { sender.sendMove(x, y, 0, ""); }
            catch (Exception ex) { log.println("发送落子失败: " + ex.getMessage()); }
            afterMoveCheck();
        } else {
            log.println("此处不可落子");
        }
    }

    private void onClickChess(int x,int y){
        ChessGame cg = (ChessGame) game;
        int v = cg.get(x,y);

        // 未选中 -> 选自己子并给出提示
        if (sel == null){
            if (v==0 || !cg.isMyPiece(v)){ log.println("请选择你方棋子"); return; }
            sel = new Point(x,y);
            moveHints = cg.legalTargets(x,y);
            repaint();
            return;
        }

        // 已有选中：若点到自己子 -> 切换选中并更新提示
        if (v!=0 && cg.isMyPiece(v)){
            sel = new Point(x,y);
            moveHints = cg.legalTargets(x,y);
            repaint();
            return;
        }

        if (!game.myTurn()){ log.println("现在不是你的回合"); return; }

        // 尝试走子
        int fx = sel.x, fy = sel.y;
        if (cg.move(fx,fy,x,y)){
            lastMine = new Point(x,y);
            sel = null; moveHints.clear();
            repaint();
            try {
                sender.sendMove(x, y, 0, ""); // 如果网络层支持 fx/fy，可在那边带上
            } catch (Exception ex) {
                log.println("发送走子失败: " + ex.getMessage());
            }
            afterMoveCheck();
        } else {
            log.println("非法走子");
        }
    }

    private void onClickCheckers(int x,int y){
        CheckersGame ck = (CheckersGame) game;
        int raw = ck.rawAt(x,y);

        if (sel == null){
            if (raw==0 || !ck.isMyPiece(raw)){ log.println("请选择你方棋子"); return; }
            sel = new Point(x,y);
            moveHints = ck.legalTargets(x,y);
            repaint();
            return;
        }

        if (ck.isMyPiece(raw)){
            sel = new Point(x,y);
            moveHints = ck.legalTargets(x,y);
            repaint();
            return;
        }

        if (!game.myTurn()){ log.println("现在不是你的回合"); return; }

        int fx = sel.x, fy = sel.y;
        if (ck.move(fx,fy,x,y)){
            lastMine = new Point(x,y);
            sel = null; moveHints.clear();
            repaint();
            try { sender.sendMove(x,y,0,""); }
            catch (Exception ex){ log.println("发送失败: " + ex.getMessage()); }
            afterMoveCheck();
        } else {
            log.println("此处不可落子");
        }
    }

    private void onClickBattle(int viewX,int viewY,int px,int py,int cell,int x0,int y0){
        // 这里沿用你的海战棋逻辑；若命中/落空/开火成功请设置 lastMine/lastOpp 对应模型坐标以便高亮
    }

    // === NetEventListener ===
    @Override
    public void onGameSelected(GameType type, String starter){
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

        // 背景
        g.setColor(new Color(240, 230, 200));
        g.fillRect(x0, y0, w, h);

        // 棋盘/棋子
        if (game.type()==GameType.CHESS){
            // 黑白格
            for (int my=0; my<n; my++) for (int mx=0; mx<n; mx++){
                int vx = toView(mx), vy = toView(my);
                if (((vx+vy)&1)==1) g.setColor(new Color(181,136,99));
                else g.setColor(new Color(240,217,181));
                g.fillRect(x0+vx*cell, y0+vy*cell, cell, cell);
            }
            // 选中虚线 & 提示
            Graphics2D g2 = (Graphics2D) g;
            if (sel != null){
                int vx = toView(sel.x), vy = toView(sel.y);
                float[] dash = {6f,6f};
                Stroke old = g2.getStroke();
                g2.setColor(new Color(220,70,70));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, dash, 0f));
                g2.drawRect(x0+vx*cell+2, y0+vy*cell+2, cell-4, cell-4);
                g2.setStroke(old);
            }
            if (!moveHints.isEmpty()){
                g.setColor(new Color(80,200,120,120));
                for (Point p : moveHints){
                    int vx = toView(p.x), vy = toView(p.y);
                    g.fillRect(x0+vx*cell+4, y0+vy*cell+4, cell-8, cell-8);
                }
            }
            // 棋子（Unicode）
            ChessGame cg = (ChessGame) game;
            for (int my=0; my<n; my++) for (int mx=0; mx<n; mx++){
                int v = cg.get(mx,my);
                if (v==0) continue;
                String sym = chessSymbol(v);
                int vx = toView(mx), vy = toView(my);
                g.setColor(Color.BLACK);
                g.setFont(getFont().deriveFont(Font.PLAIN, cell*0.7f));
                FontMetrics fm = g.getFontMetrics();
                int sx = x0+vx*cell + (cell - fm.stringWidth(sym))/2;
                int sy = y0+vy*cell + (cell + fm.getAscent()-fm.getDescent())/2 - 2;
                g.drawString(sym, sx, sy);
            }
        } else {
            // 通用网格
            g.setColor(Color.GRAY);
            for (int i=0;i<=n;i++){
                g.drawLine(x0, y0+i*cell, x0+w, y0+i*cell);
                g.drawLine(x0+i*cell, y0, x0+i*cell, y0+h);
            }
            // 其它棋子（含 Checkers/Gomoku/Reversi）
            for (int my=0; my<n; my++) for (int mx=0; mx<n; mx++){
                int v = game.get(mx, my);
                if (v==0) continue;
                int vx = toView(mx), vy = toView(my);
                g.setColor(v==1? Color.BLACK : Color.WHITE);
                g.fillOval(x0+vx*cell+4, y0+vy*cell+4, cell-8, cell-8);
                g.setColor(Color.DARK_GRAY);
                g.drawOval(x0+vx*cell+4, y0+vy*cell+4, cell-8, cell-8);
            }
            // Checkers 的选中+提示
            if (game.type()==GameType.CHECKERS){
                Graphics2D g2 = (Graphics2D) g;
                if (sel != null){
                    int vx = toView(sel.x), vy = toView(sel.y);
                    float[] dash = {6f,6f};
                    Stroke old = g2.getStroke();
                    g2.setColor(new Color(220,70,70));
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER, 10f, dash, 0f));
                    g2.drawRect(x0+vx*cell+2, y0+vy*cell+2, cell-4, cell-4);
                    g2.setStroke(old);
                }
                if (!moveHints.isEmpty()){
                    g.setColor(new Color(80,200,120,120));
                    for (Point p : moveHints){
                        int vx = toView(p.x), vy = toView(p.y);
                        g.fillRect(x0+vx*cell+4, y0+vy*cell+4, cell-8, cell-8);
                    }
                }
            }
        }

        // 最新一步高亮（绿色=我，红框=对方）
        if (lastMine != null){
            int vx = toView(lastMine.x), vy = toView(lastMine.y);
            g.setColor(new Color(46, 204, 113, 160));
            g.fillRect(x0+vx*cell, y0+vy*cell, cell, cell);
        }
        if (lastOpp != null){
            int vx = toView(lastOpp.x), vy = toView(lastOpp.y);
            g.setColor(new Color(231, 76, 60, 150));
            g.drawRect(x0+vx*cell, y0+vy*cell, cell-1, cell-1);
            g.drawRect(x0+vx*cell+1, y0+vy*cell+1, cell-3, cell-3);
        }

        // 状态行
        g.setColor(Color.DARK_GRAY);
        g.drawString("当前：" + (game.myTurn() ? "你的回合" : "对方回合") + " | " + game.resultText(), x0, y0+h+16);
    }

    private String chessSymbol(int v){
        // 白(>0)：♙♘♗♖♕♔   黑(<0)：♟♞♝♜♛♚
        int a = Math.abs(v);
        boolean white = v>0;
        return switch (a){
            case ChessGame.WP -> white ? "♙" : "♟";
            case ChessGame.WN -> white ? "♘" : "♞";
            case ChessGame.WB -> white ? "♗" : "♝";
            case ChessGame.WR -> white ? "♖" : "♜";
            case ChessGame.WQ -> white ? "♕" : "♛";
            case ChessGame.WK -> white ? "♔" : "♚";
            default -> "?";
        };
    }

    public void manualReset(boolean hostStartsNow){
        boolean iStart = hostStartsNow == hostSide;
        setGame(game.type(), iStart);
    }

    public GameType currentType(){ return game.type(); }
}

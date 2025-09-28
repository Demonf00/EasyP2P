package com.easy.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import com.easy.game.*;
import com.easy.net.Proto;

public class BoardCanvas extends JPanel implements MoveListener, NetEventListener {

    private Game game;
    private final MoveSender sender;
    private final ConsoleSink log;

    private boolean hostSide = true;     // 我是否房主
    private boolean hostStartsNext = true; // 全局：下一局由谁先（Host/Client 交替）

    // 高亮
    private Point lastMine = null;
    private Point lastOpp  = null;

    // 选中与可落点（用于棋盘需要起点->终点的游戏）
    private Point sel = null;
    private java.util.List<Point> legal = java.util.Collections.emptyList();

    // —— Battleship 专用输入状态（右键旋转）——
    private int bsHoverX = -1, bsHoverY = -1;
    private boolean bsRotate = false; // false=横放，true=竖放

    public BoardCanvas(MoveSender sender, ConsoleSink log){
        this.sender = sender;
        this.log = log;
        setPreferredSize(new Dimension(620, 620));

        // 点击改为 mousePressed（更灵敏）
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                onPress(e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                // 不使用 clicked，避免双击/系统延迟
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (game != null && game.type() == GameType.BATTLE) {
                    int[] m = mapToGrid(e.getX(), e.getY());
                    bsHoverX = m[0]; bsHoverY = m[1];
                    repaint();
                }
            }
        });

        setGame(GameType.GOMOKU, /*iStart*/ true);
    }

    /** 由 MainFrame/Sidebar 通知我是不是 Host（影响先后手换边等文字提示） */
    public void setHost(boolean host){ this.hostSide = host; }

    /** 由 MainFrame 注入“下一局由谁先”（全局交替策略在 MainFrame 持有） */
    public void setHostStartsNext(boolean hostStarts) { this.hostStartsNext = hostStarts; }

    /** 当前棋种 */
    public GameType currentType(){ return game==null? GameType.GOMOKU : game.type(); }

    /** 手动重置（不改变棋种），由 Sidebar 的“重置整局”触发 */
    public void manualReset(boolean hostStartsNow){
        clearHints();
        boolean iStart = (hostStartsNow == hostSide);
        setGame(game.type(), iStart);
    }

    /** 切换棋种（Host 选择/Client 接收），按“全局交替策略”决定先后 */
    private void switchGame(GameType type){
        clearHints();
        boolean iStart = (hostStartsNext == hostSide);
        setGame(type, iStart);
    }

    private void clearHints(){
        lastMine = lastOpp = sel = null;
        legal = java.util.Collections.emptyList();
    }

    /** 初始化/重开一局（按 iStart 确定我是否先手） */
    public void setGame(GameType type, boolean iStart){
        switch (type) {
            case REVERSI -> game = new ReversiGame();
            case CHECKERS -> game = new CheckersGame();
            case CHESS -> game = new ChessGame();
            case BATTLE -> game = new BattleshipGame();
            default -> game = new GomokuGame();
        }
        game.reset(iStart);
        game.setMyTurn(iStart);
        log.println("游戏开始：" + type + "，" + (iStart ? "你先手" : "你后手"));
        repaint();
    }

    // ====== 输入映射 =======

    private void onPress(MouseEvent e){
        if (game == null) return;

        // 右键 = 海战棋旋转
        if (game.type() == GameType.BATTLE && SwingUtilities.isRightMouseButton(e)) {
            bsRotate = !bsRotate;
            repaint();
            return;
        }

        int[] m = mapToGrid(e.getX(), e.getY());
        int x = m[0], y = m[1];
        if (x < 0 || y < 0) return;

        switch (game.type()){
            case CHESS -> pressChess(x,y);
            case CHECKERS -> pressCheckers(x,y);
            case BATTLE -> pressBattle(x,y);
            default -> pressPointGames(x,y); // Gomoku / Reversi
        }
    }

    /** 将像素坐标映射为当前棋盘格子坐标（不在棋盘内返回 -1,-1） */
    private int[] mapToGrid(int px,int py){
        int n = game.size();
        int cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n));
        int w = cell * n, h = cell * n;
        int x0 = (getWidth()  - w) / 2;
        int y0 = (getHeight() - h) / 2;

        int gx = (px - x0) / cell;
        int gy = (py - y0) / cell;
        if (px < x0 || py < y0 || px >= x0+w || py >= y0+h) return new int[]{-1,-1};
        return new int[]{gx, gy};
    }

    // ====== 单点落子的棋（Gomoku / Reversi） ======

    private void pressPointGames(int x,int y){
        if (!game.myTurn()) { log.println("现在不是你的回合"); return; }
        if (game.play(x,y)) {
            lastMine = new Point(x,y);
            repaint();
            try {
                sender.sendMove(x,y,0,"");
            } catch (Exception ex) { log.println("发送落子失败: " + ex.getMessage()); }
            afterMoveCheck();
        } else {
            log.println("此处不可落子");
        }
    }

    // ====== Chess ======

    private void pressChess(int x,int y){
        ChessGame cg = (ChessGame) game;
        int v = cg.get(x,y);

        if (sel == null) {
            if (v==0 || !cg.isMyPiece(v)) { log.println("请选择你方棋子"); return; }
            sel = new Point(x,y);
            legal = cg.legalMovesFrom(x,y);
            repaint();
            return;
        }

        // 再点到己方棋子 -> 改变选中
        if (v!=0 && cg.isMyPiece(v)) {
            sel = new Point(x,y);
            legal = cg.legalMovesFrom(x,y);
            repaint();
            return;
        }

        if (!game.myTurn()) { log.println("现在不是你的回合"); return; }
        int fx = sel.x, fy = sel.y;
        if (cg.move(fx,fy,x,y)) {
            lastMine = new Point(x,y);
            sel = null; legal = java.util.Collections.emptyList();
            repaint();
            try {
                if (sender instanceof com.easy.net.NetServer s) s.sendJson(Proto.moveFxFy(fx,fy,x,y));
                else if (sender instanceof com.easy.net.NetClient c) c.sendJson(Proto.moveFxFy(fx,fy,x,y));
            } catch (Exception ex) { log.println("发送走子失败: " + ex.getMessage()); }
            afterMoveCheck();
        } else {
            log.println("非法走子");
        }
    }

    // ====== Checkers ======

    private void pressCheckers(int x,int y){
        CheckersGame ck = (CheckersGame) game;

        if (sel == null) {
            if (!ck.isMyPieceAt(x,y)) { log.println("请选择你方棋子"); return; }
            sel = new Point(x,y);
            legal = ck.legalMovesFrom(x,y);
            repaint();
            return;
        }

        if (ck.isMyPieceAt(x,y)) {
            sel = new Point(x,y);
            legal = ck.legalMovesFrom(x,y);
            repaint();
            return;
        }

        if (!game.myTurn()) { log.println("现在不是你的回合"); return; }
        int fx = sel.x, fy = sel.y;
        if (ck.move(fx,fy,x,y)) {
            lastMine = new Point(x,y);
            sel = null; legal = java.util.Collections.emptyList();
            repaint();
            try {
                if (sender instanceof com.easy.net.NetServer s) s.sendJson(Proto.moveFxFy(fx,fy,x,y));
                else if (sender instanceof com.easy.net.NetClient c) c.sendJson(Proto.moveFxFy(fx,fy,x,y));
            } catch (Exception ex) { log.println("发送走子失败: " + ex.getMessage()); }
            afterMoveCheck();
        } else {
            log.println("非法走子");
        }
    }

    // ====== Battleship（双盘、布阵、Ready、开火） ======

    private void pressBattle(int x,int y){
        BattleshipGame bg = (BattleshipGame) game;

        if (bg.phase()==BattleshipGame.Phase.SETUP){
            // 自己盘上才可布置
            if (!bg.onOwnBoard(y)) { return; }
            boolean ok = bg.placeOrRotateAt(x,y, bsRotate);
            if (!ok) { log.println("此处不能放置船"); }
            repaint();
            return;
        }

        if (bg.phase()==BattleshipGame.Phase.PLAY){
            if (!game.myTurn()){ log.println("现在不是你的回合"); return; }
            // 只能打对方盘（下半盘）
            if (!bg.onEnemyBoard(y)) { return; }
            BattleshipGame.FireResult fr = bg.fireAtEnemy(x,y);
            repaint();
            try {
                if (sender instanceof com.easy.net.NetServer s) s.sendJson(Proto.battleFire(x,y));
                else if (sender instanceof com.easy.net.NetClient c) c.sendJson(Proto.battleFire(x,y));
            } catch (Exception ex){ log.println("发送开火失败: " + ex.getMessage()); }
            if (fr == BattleshipGame.FireResult.WIN){ log.println("你击沉了对方所有舰船！"); }
            afterMoveCheck();
        }
    }

    // ====== 回合结束/胜负 =======

    private void afterMoveCheck(){
        if (game.isFinished()){
            // 下一局由对方先
            hostStartsNext = !hostStartsNext;
            log.println("游戏结束：" + game.resultText() + "。3 秒后重开下一局并交换先手。");
            Timer t = new Timer(3000, e -> {
                clearHints();
                setGame(game.type(), hostStartsNext == hostSide);
            });
            t.setRepeats(false); t.start();
        }
    }

    // ====== 来自网络的对方走子 ======

    @Override
    public void onOpponentMove(int x, int y){
        // 仅兼容 Gomoku/Reversi/battle-hit-only 情况，带起点的见 onOpponentMoveFxFy
        SwingUtilities.invokeLater(() -> {
            switch (game.type()){
                case CHESS, CHECKERS -> { /* 忽略（这两者用 fx,fy 通道）*/ }
                case BATTLE -> {
                    BattleshipGame bg = (BattleshipGame) game;
                    bg.applyEnemyFire(x,y); // 对方打我
                    lastOpp = new Point(x,y);
                    repaint();
                    afterMoveCheck();
                }
                default -> {
                    if (game.play(x,y)){
                        lastOpp = new Point(x,y);
                        repaint();
                        afterMoveCheck();
                    }
                }
            }
        });
    }

    public void onOpponentMoveFxFy(int fx,int fy,int x,int y){
        SwingUtilities.invokeLater(() -> {
            switch (game.type()){
                case CHESS -> {
                    ChessGame cg = (ChessGame) game;
                    if (cg.moveFromPeer(fx,fy,x,y)) {
                        lastOpp = new Point(x,y);
                        sel = null; legal = java.util.Collections.emptyList();
                        repaint();
                        afterMoveCheck();
                    }
                }
                case CHECKERS -> {
                    CheckersGame ck = (CheckersGame) game;
                    if (ck.moveFromPeer(fx,fy,x,y)) {
                        lastOpp = new Point(x,y);
                        sel = null; legal = java.util.Collections.emptyList();
                        repaint();
                        afterMoveCheck();
                    }
                }
                default -> {}
            }
        });
    }

    // ====== NetEventListener ======

    @Override
    public void onGameSelected(GameType type, String starter){
        // 无论谁发起，严格走全局交替（由 MainFrame 传给我 hostStartsNext）
        SwingUtilities.invokeLater(() -> switchGame(type));
    }
    @Override
    public void onGameSuggested(GameType type){
        log.println("对方建议切换游戏：" + type);
    }

    // ====== 绘制 ======

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        if (game == null) return;

        int n = game.size();
        int cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n));
        int w = cell * n, h = cell * n;
        int x0 = (getWidth()-w)/2, y0 = (getHeight()-h)/2;

        if (game.type()==GameType.CHESS){
            // 棋盘底色
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                boolean dark = ((xx+yy)&1)==1;
                g.setColor(dark? new Color(181,136,99) : new Color(240,217,181));
                g.fillRect(x0+xx*cell, y0+yy*cell, cell, cell);
            }
            // 选中格与可落点
            if (sel!=null){
                g.setColor(new Color(255,0,0,160));
                g.drawRect(x0+sel.x*cell, y0+sel.y*cell, cell-1, cell-1);
                g.drawRect(x0+sel.x*cell+1, y0+sel.y*cell+1, cell-3, cell-3);
            }
            g.setColor(new Color(46, 204, 113, 140));
            for (Point p : legal){
                g.fillRect(x0+p.x*cell, y0+p.y*cell, cell, cell);
            }
            // 棋子（Unicode）
            ChessGame cg = (ChessGame) game;
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                int v = cg.get(xx, yy);
                if (v==0) continue;
                String sym = chessSymbol(v);
                g.setColor(Color.BLACK);
                g.setFont(getFont().deriveFont(Font.PLAIN, cell*0.7f));
                FontMetrics fm = g.getFontMetrics();
                int sx = x0+xx*cell + (cell - fm.stringWidth(sym))/2;
                int sy = y0+yy*cell + (cell + fm.getAscent()-fm.getDescent())/2 - 2;
                g.drawString(sym, sx, sy);
            }
        } else if (game.type()==GameType.BATTLE){
            BattleshipGame bg = (BattleshipGame) game;
            // 上：自己盘，下：敌人盘（中间留 12px）
            int hgap = 12;
            int halfH = (h - hgap)/2;

            // 画一个盘方法
            java.util.function.BiConsumer<Boolean,Integer> drawBoard = (isMine, topY) -> {
                // 背景
                g.setColor(new Color(230,230,230));
                g.fillRect(x0, topY, w, halfH);
                // 网格
                g.setColor(Color.GRAY);
                for (int i=0;i<=n;i++){
                    g.drawLine(x0, topY+i*(halfH/n), x0+w, topY+i*(halfH/n));
                    g.drawLine(x0+i*(w/n), topY, x0+i*(w/n), topY+halfH);
                }
                // 内容
                bg.paintBoard(g, x0, topY, w/n, halfH/n, isMine, bsHoverX, bsHoverY, bsRotate);
            };
            drawBoard.accept(true, y0);
            drawBoard.accept(false, y0+halfH+hgap);
        } else {
            // 通用背景 + 网格
            g.setColor(new Color(240, 230, 200));
            g.fillRect(x0, y0, w, h);
            g.setColor(Color.GRAY);
            for (int i=0;i<=n;i++){
                g.drawLine(x0, y0+i*cell, x0+w, y0+i*cell);
                g.drawLine(x0+i*cell, y0, x0+i*cell, y0+h);
            }
            // 棋子
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                int v = game.get(xx, yy);
                if (v==0) continue;
                g.setColor(v==1? Color.BLACK : Color.WHITE);
                g.fillOval(x0+xx*cell+4, y0+yy*cell+4, cell-8, cell-8);
                g.setColor(Color.DARK_GRAY);
                g.drawOval(x0+xx*cell+4, y0+yy*cell+4, cell-8, cell-8);
            }
            // 检查/棋：高亮
            g.setColor(new Color(46, 204, 113, 140));
            for (Point p : legal){
                g.fillRect(x0+p.x*cell, y0+p.y*cell, cell, cell);
            }
            if (sel!=null){
                g.setColor(new Color(255,0,0,160));
                g.drawRect(x0+sel.x*cell, y0+sel.y*cell, cell-1, cell-1);
                g.drawRect(x0+sel.x*cell+1, y0+sel.y*cell+1, cell-3, cell-3);
            }
        }

        // 最新一步高亮（绿=我，红=对手）
        if (game.type()!=GameType.BATTLE){
            int n2 = n;
            cell = Math.max(24, Math.min(48, Math.min(getWidth(), getHeight()) / n2));
            int w2 = cell*n2, h2=cell*n2;
            int X0 = (getWidth()-w2)/2, Y0=(getHeight()-h2)/2;
            if (lastMine != null){
                g.setColor(new Color(46, 204, 113, 160));
                g.fillRect(X0+lastMine.x*cell, Y0+lastMine.y*cell, cell, cell);
            }
            if (lastOpp != null){
                g.setColor(new Color(231, 76, 60, 150));
                g.drawRect(X0+lastOpp.x*cell, Y0+lastOpp.y*cell, cell-1, cell-1);
                g.drawRect(X0+lastOpp.x*cell+1, Y0+lastOpp.y*cell+1, cell-3, cell-3);
            }
        }

        // 状态栏
        g.setColor(Color.DARK_GRAY);
        g.drawString("当前：" + (game.myTurn()? "你的回合" : "对方回合") + " | " + game.resultText(), 10, getHeight()-10);
    }

    private String chessSymbol(int v){
        int a = Math.abs(v);
        boolean white = v>0;
        return switch (a){
            case ChessGame.WP -> white? "♙" : "♟";
            case ChessGame.WN -> white? "♘" : "♞";
            case ChessGame.WB -> white? "♗" : "♝";
            case ChessGame.WR -> white? "♖" : "♜";
            case ChessGame.WQ -> white? "♕" : "♛";
            case ChessGame.WK -> white? "♔" : "♚";
            default -> "?";
        };
    }
}

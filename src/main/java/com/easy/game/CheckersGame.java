package com.easy.game;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** English Checkers / Draughts（简化：单段吃子，升王） */
public class CheckersGame implements Game {
    private static final int EMPTY=0, P1=1, P2=2, K1=3, K2=4;

    private final int N = 8;
    private final int[][] b = new int[N][N];

    private boolean iAmP1 = true;
    private boolean myTurn = true;

    private boolean finished = false;
    private String result = "";
    private Point lastFrom, lastTo;

    @Override public GameType type(){ return GameType.CHECKERS; }
    @Override public int size(){ return N; }
    @Override public boolean myTurn(){ return myTurn && !finished; }
    @Override public int currentPlayer(){ return myTurn?1:2; }
    @Override public boolean isFinished(){ return finished; }
    @Override public String resultText(){ return result; }
    @Override public void setMyTurn(boolean v){ this.myTurn = v; }

    @Override
    public void reset(boolean iStart){
        iAmP1   = iStart;
        myTurn  = iStart;
        finished=false; result=""; lastFrom=lastTo=null;
        for (int y=0;y<N;y++) for (int x=0;x<N;x++) b[x][y]=EMPTY;
        for (int y=0;y<3;y++) for (int x=0;x<N;x++) if(((x+y)&1)==1) b[x][y]=P2;
        for (int y=N-3;y<N;y++) for (int x=0;x<N;x++) if(((x+y)&1)==1) b[x][y]=P1;
    }

    /** 绘制用：1=黑方，2=白方，其它=空 */
    @Override public int get(int x,int y){
        int v=b[x][y];
        if (v==EMPTY) return 0;
        return (v==P1||v==K1) ? 1 : 2;
    }

    /** UI/逻辑辅助：获取原始格值 */
    public int rawAt(int x,int y){ return b[x][y]; }

    /** 是否我方棋子（基于原始格值） */
    public boolean isMyPiece(int raw){
        return iAmP1 ? (raw==P1 || raw==K1) : (raw==P2 || raw==K2);
    }
    public boolean isMyPieceAt(int x,int y){ return isMyPiece(b[x][y]); }

    /** 给 UI：从(x,y) 可走的所有目标(终点) */
    public List<Point> legalTargets(int x,int y){
        if (!in(x,y)) return Collections.emptyList();
        int v = b[x][y];
        if (v==EMPTY || !isMyPiece(v)) return Collections.emptyList();
        return legalMovesFrom(x,y);
    }

    private boolean in(int x,int y){ return x>=0&&x<N&&y>=0&&y<N; }
    private boolean mine(int v){ return v==P1||v==K1; }
    private boolean opp (int v){ return v==P2||v==K2; }
    private boolean isKing(int v){ return v==K1||v==K2; }

    public boolean move(int fx,int fy,int tx,int ty){
        if (!myTurn || finished) return false;
        if (!in(fx,fy)||!in(tx,ty)) return false;
        int v=b[fx][fy]; if (v==EMPTY) return false;

        // 我方颜色限制
        if (iAmP1 && !mine(v)) return false;
        if (!iAmP1 && !opp(v)) return false;

        boolean ok=false, jumped=false;
        for (Point p: legalMovesFrom(fx,fy)){
            if (p.x==tx && p.y==ty){ ok=true; jumped=Math.abs(tx-fx)==2; break; }
        }
        if (!ok) return false;

        int cx=0,cy=0;
        if (jumped){ cx=(fx+tx)/2; cy=(fy+ty)/2; }

        b[tx][ty]=v; b[fx][fy]=EMPTY;
        if (jumped) b[cx][cy]=EMPTY;

        if (v==P1 && ty==0) b[tx][ty]=K1;
        if (v==P2 && ty==N-1) b[tx][ty]=K2;

        lastFrom=new Point(fx,fy); lastTo=new Point(tx,ty);
        // 胜负（对手无子或无路）
        if (noPieces(!iAmP1) || noMoves(!iAmP1)) { finished=true; result="先手胜"; }

        myTurn = false;
        return true;
    }

    /** 对方走子应用（不做颜色限制），走完后轮到我 */
    public boolean moveFromPeer(int fx,int fy,int tx,int ty){
        if (!in(fx,fy)||!in(tx,ty)||finished) return false;
        int v=b[fx][fy]; if (v==EMPTY) return false;

        boolean ok=false, jumped=false;
        for (Point p: legalMovesFromPeer(fx,fy)){
            if (p.x==tx && p.y==ty){ ok=true; jumped=Math.abs(tx-fx)==2; break; }
        }
        if (!ok) return false;

        int cx=0,cy=0;
        if (jumped){ cx=(fx+tx)/2; cy=(fy+ty)/2; }

        b[tx][ty]=v; b[fx][fy]=EMPTY;
        if (jumped) b[cx][cy]=EMPTY;

        if (v==P1 && ty==0) b[tx][ty]=K1;
        if (v==P2 && ty==N-1) b[tx][ty]=K2;

        lastFrom=new Point(fx,fy); lastTo=new Point(tx,ty);

        myTurn = true;
        return true;
    }

    /** 我方可落到的终点 */
    private List<Point> legalMovesFrom(int x,int y){
        int v=b[x][y]; List<Point> res=new ArrayList<>();
        if (v==EMPTY) return res;
        if (isKing(v)){
            tryStep(x,y, 1, 1,res,true);
            tryStep(x,y,-1, 1,res,true);
            tryStep(x,y, 1,-1,res,true);
            tryStep(x,y,-1,-1,res,true);
        }else if (v==P1){
            tryStep(x,y, 1,-1,res,false);
            tryStep(x,y,-1,-1,res,false);
        }else{ // P2
            tryStep(x,y, 1, 1,res,false);
            tryStep(x,y,-1, 1,res,false);
        }
        return res;
    }

    /** 对方走子的合法终点（不考虑我方/对方身份，只按棋规生成） */
    private List<Point> legalMovesFromPeer(int x,int y){
        int v=b[x][y]; List<Point> res=new ArrayList<>();
        if (v==EMPTY) return res;
        if (isKing(v)){
            tryStepPeer(x,y, 1, 1,res,true);
            tryStepPeer(x,y,-1, 1,res,true);
            tryStepPeer(x,y, 1,-1,res,true);
            tryStepPeer(x,y,-1,-1,res,true);
        }else if (v==P1){
            tryStepPeer(x,y, 1,-1,res,false);
            tryStepPeer(x,y,-1,-1,res,false);
        }else{ // P2
            tryStepPeer(x,y, 1, 1,res,false);
            tryStepPeer(x,y,-1, 1,res,false);
        }
        return res;
    }

    private void tryStep(int x,int y,int dx,int dy,List<Point> res, boolean king){
        int nx=x+dx, ny=y+dy;
        if (in(nx,ny) && b[nx][ny]==EMPTY) res.add(new Point(nx,ny));
        int jx=x+2*dx, jy=y+2*dy, mx=x+dx, my=y+dy;
        if (in(jx,jy) && b[jx][jy]==EMPTY && in(mx,my) && opp(b[mx][my])) res.add(new Point(jx,jy));
    }
    private void tryStepPeer(int x,int y,int dx,int dy,List<Point> res, boolean king){
        int nx=x+dx, ny=y+dy;
        if (in(nx,ny) && b[nx][ny]==EMPTY) res.add(new Point(nx,ny));
        int jx=x+2*dx, jy=y+2*dy, mx=x+dx, my=y+dy;
        if (in(jx,jy) && b[jx][jy]==EMPTY && in(mx,my) && b[mx][my]!=EMPTY) res.add(new Point(jx,jy));
    }

    private boolean noPieces(boolean forP1){
        for (int y=0;y<N;y++) for (int x=0;x<N;x++){
            int v=b[x][y];
            if (forP1 && (v==P1||v==K1)) return false;
            if (!forP1 && (v==P2||v==K2)) return false;
        }
        return true;
    }
    private boolean noMoves(boolean forP1){
        boolean saveTurn = myTurn; myTurn = forP1;
        for (int y=0;y<N;y++) for (int x=0;x<N;x++){
            if ((forP1 ? mine(b[x][y]) : opp(b[x][y])) && !legalMovesFrom(x,y).isEmpty()){
                myTurn = saveTurn; return false;
            }
        }
        myTurn = saveTurn; return true;
    }

    @Override public boolean play(int x,int y){ return false; }

    public Point lastFrom(){ return lastFrom; }
    public Point lastTo(){ return lastTo; }
}

package com.easy.game;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/** 最小版国际象棋（不含易位/吃过路兵），吃王判胜；带 legalMovesFrom 供 UI 高亮 */
public class ChessGame implements Game {

    public static final int WP=1, WN=2, WB=3, WR=4, WQ=5, WK=6;
    public static final int BP=-1, BN=-2, BB=-3, BR=-4, BQ=-5, BK=-6;

    private final int N = 8;
    private final int[][] b = new int[N][N];

    private boolean iAmWhite = true;
    private boolean myTurn = true;

    private boolean finished = false;
    private String result = "";

    @Override public GameType type(){ return GameType.CHESS; }
    @Override public int size(){ return N; }
    @Override public boolean myTurn(){ return myTurn && !finished; }
    @Override public int currentPlayer(){ return myTurn?1:2; }
    @Override public boolean isFinished(){ return finished; }
    @Override public String resultText(){ return result==null? "" : result; }
    @Override public void setMyTurn(boolean v){ this.myTurn = v; }

    @Override
    public void reset(boolean iStart){
        iAmWhite = iStart;
        myTurn   = iStart;
        finished = false;
        result   = "";
        for (int y=0;y<N;y++) for (int x=0;x<N;x++) b[x][y]=0;
        // 白
        b[0][7]=WR; b[1][7]=WN; b[2][7]=WB; b[3][7]=WQ; b[4][7]=WK; b[5][7]=WB; b[6][7]=WN; b[7][7]=WR;
        for (int x=0;x<N;x++) b[x][6]=WP;
        // 黑
        b[0][0]=BR; b[1][0]=BN; b[2][0]=BB; b[3][0]=BQ; b[4][0]=BK; b[5][0]=BB; b[6][0]=BN; b[7][0]=BR;
        for (int x=0;x<N;x++) b[x][1]=BP;
    }

    @Override public int get(int x,int y){ return b[x][y]; }

    public boolean isMyPiece(int v){
        if (v==0) return false;
        return (iAmWhite && v > 0) || (!iAmWhite && v < 0);
    }

    public List<Point> legalMovesFrom(int fx,int fy){
        List<Point> res = new ArrayList<>();
        if (!in(fx,fy)) return res;
        int v = b[fx][fy];
        if (v==0) return res;
        if (!isMyPiece(v)) return res;

        for (int ty=0; ty<N; ty++){
            for (int tx=0; tx<N; tx++){
                if (isLegalFromTo(fx,fy,tx,ty)) res.add(new Point(tx,ty));
            }
        }
        return res;
    }

    public boolean move(int fx,int fy,int tx,int ty){
        if (!myTurn || finished) return false;
        if (!in(fx,fy) || !in(tx,ty)) return false;
        int v=b[fx][fy];
        if (v==0 || !isMyPiece(v)) return false;
        if (!isLegalFromTo(fx,fy,tx,ty)) return false;

        int captured = b[tx][ty];
        b[tx][ty] = v; b[fx][fy] = 0;
        // 升变
        if (v==WP && ty==0) b[tx][ty]=WQ;
        if (v==BP && ty==7) b[tx][ty]=BQ;

        if (captured == WK){ finished = true; result = "黑方胜"; }
        if (captured == BK){ finished = true; result = "白方胜"; }

        myTurn = false;
        return true;
    }

    public boolean moveFromPeer(int fx,int fy,int tx,int ty){
        if (!in(fx,fy) || !in(tx,ty) || finished) return false;
        int v=b[fx][fy]; if (v==0) return false;
        if (!isLegalFromTo(fx,fy,tx,ty)) return false;

        int captured = b[tx][ty];
        b[tx][ty] = v; b[fx][fy] = 0;
        if (v==WP && ty==0) b[tx][ty]=WQ;
        if (v==BP && ty==7) b[tx][ty]=BQ;

        if (captured == WK){ finished = true; result = "黑方胜"; }
        if (captured == BK){ finished = true; result = "白方胜"; }

        myTurn = true;
        return true;
    }

    private boolean in(int x,int y){ return x>=0 && x<N && y>=0 && y<N; }

    private boolean isLegalFromTo(int fx,int fy,int tx,int ty){
        int v = b[fx][fy];
        int dst = b[tx][ty];
        if (v==0) return false;
        if (dst!=0 && Integer.signum(dst)==Integer.signum(v)) return false;

        int piece = Math.abs(v);
        return switch (piece) {
            case 1 -> { // pawn
                int dir = v>0 ? -1 : 1; // 白向上，黑向下
                boolean ok=false;
                if (tx==fx && dst==0){
                    if (ty==fy+dir) ok=true;
                    if ((v==WP && fy==6 || v==BP && fy==1) && ty==fy+2*dir && b[fx][fy+dir]==0) ok=true;
                }
                if (Math.abs(tx-fx)==1 && ty==fy+dir && dst!=0) ok=true;
                yield ok;
            }
            case 2 -> { int dx=Math.abs(tx-fx), dy=Math.abs(ty-fy); yield dx*dy==2; } // knight
            case 3 -> clearLine(fx,fy,tx,ty, 1,1) || clearLine(fx,fy,tx,ty, 1,-1) || clearLine(fx,fy,tx,ty, -1,1) || clearLine(fx,fy,tx,ty, -1,-1); // bishop
            case 4 -> clearLine(fx,fy,tx,ty, 1,0) || clearLine(fx,fy,tx,ty, -1,0) || clearLine(fx,fy,tx,ty, 0,1) || clearLine(fx,fy,tx,ty, 0,-1); // rook
            case 5 -> isLegalFromTo(fx,fy,tx,ty,3) || isLegalFromTo(fx,fy,tx,ty,4); // queen
            case 6 -> { int dx=Math.abs(tx-fx), dy=Math.abs(ty-fy); yield dx<=1 && dy<=1; } // king
            default -> false;
        };
    }
    private boolean isLegalFromTo(int fx,int fy,int tx,int ty,int kind){
        if (kind==3){
            return clearLine(fx,fy,tx,ty, 1,1) || clearLine(fx,fy,tx,ty, 1,-1) || clearLine(fx,fy,tx,ty, -1,1) || clearLine(fx,fy,tx,ty, -1,-1);
        }
        if (kind==4){
            return clearLine(fx,fy,tx,ty, 1,0) || clearLine(fx,fy,tx,ty, -1,0) || clearLine(fx,fy,tx,ty, 0,1) || clearLine(fx,fy,tx,ty, 0,-1);
        }
        return false;
    }
    private boolean clearLine(int fx,int fy,int tx,int ty,int dx,int dy){
        int ddx = Integer.compare(tx,fx), ddy = Integer.compare(ty,fy);
        if (dx==1 && dy==1 && Math.abs(tx-fx)!=Math.abs(ty-fy)) return false;
        if (dx==1 && dy==0 && fy!=ty) return false;
        if (dx==0 && dy==1 && fx!=tx) return false;

        int x=fx+ddx, y=fy+ddy;
        while (x!=tx || y!=ty){
            if (b[x][y]!=0) return false;
            x+=ddx; y+=ddy;
        }
        return true;
    }

    @Override public boolean play(int x,int y){ return false; }
}

package com.easy.game;

import java.awt.Point;
import java.util.Arrays;

public class BattleshipGame implements Game {

    public enum Phase { SETUP, PLAY, END }

    private final int N = 10;
    private final int[][] mine = new int[N][N]; // 0空 1船 2命中(HIT) 3落空(MISS)
    private final int[][] opp  = new int[N][N]; // 0未知 2命中 3落空（仅记录我打对面的结果）

    private boolean myTurn = true; // 当前是否轮到我
    private boolean iStart = true; // 是否我先（reset 传入）
    private Phase phase = Phase.SETUP;
    private String result = "";

    private Point lastShotMine, lastShotOpp;

    private final int[] fleet = {5,4,3,3,2};
    private int placedShips = 0;

    @Override public GameType type(){ return GameType.BATTLE; }
    @Override public int size(){ return N; }
    @Override public int get(int x,int y){ return opp[x][y]; }     // 兼容：默认返回“对方盘”用于右侧板绘
    public int getMine(int x,int y){ return mine[x][y]; }
    public int getOpp (int x,int y){ return opp[x][y]; }

    @Override public boolean myTurn(){ return myTurn && phase==Phase.PLAY; }
    @Override public int currentPlayer(){ return myTurn?1:2; }
    @Override public boolean isFinished(){ return phase==Phase.END; }
    @Override public String resultText(){ return result; }
    @Override public void setMyTurn(boolean v){ this.myTurn = v; }

    @Override
    public void reset(boolean iStart){
        this.iStart = iStart;
        for (int y=0;y<N;y++){ Arrays.fill(mine[y],0); Arrays.fill(opp[y],0); }
        phase=Phase.SETUP; placedShips=0; result=""; lastShotMine=lastShotOpp=null;
        myTurn = iStart;
    }

    public Phase phase(){ return phase; }
    public int[] fleet(){ return fleet; }
    public int placedCount(){ return placedShips; }

    public boolean placeShip(int x,int y, boolean horizontal){
        if (phase!=Phase.SETUP || placedShips>=fleet.length) return false;
        int len = fleet[placedShips];
        if (horizontal){
            if (x+len>N) return false;
            for(int i=0;i<len;i++) if (mine[x+i][y]!=0) return false;
            for(int i=0;i<len;i++) mine[x+i][y]=1;
        }else{
            if (y+len>N) return false;
            for(int i=0;i<len;i++) if (mine[x][y+i]!=0) return false;
            for(int i=0;i<len;i++) mine[x][y+i]=1;
        }
        placedShips++;
        if (placedShips==fleet.length){ phase=Phase.PLAY; myTurn=iStart; }
        return true;
    }

    /** 我方开火（对方盘）。成功返回 true；命中与否由对方回执 ackMyShot 设置。 */
    public boolean fireAtOpponent(int x,int y){
        if (phase!=Phase.PLAY || !myTurn) return false;
        if (opp[x][y]==2 || opp[x][y]==3) return false;
        // 先标落空，命中时通过回执覆盖为命中
        opp[x][y]=3;
        lastShotOpp = new Point(x,y);
        myTurn = false;
        return true;
    }

    /** 对方开火命中判定（我方盘）。返回是否命中，并推进回合到我方。 */
    public boolean onIncomingFire(int x,int y){
        if (phase!=Phase.PLAY) return false;
        boolean hit = (mine[x][y]==1);
        mine[x][y] = hit ? 2 : 3;
        lastShotMine = new Point(x,y);
        if (allSunk(mine)){ phase=Phase.END; result="对方胜"; }
        else myTurn = true;
        return hit;
    }

    /** 对刚才我的一发的回执：把 opp 的记录修正 */
    public void ackMyShot(int x,int y, boolean hit){
        if (hit) opp[x][y]=2;
        if (allSunkOpp()) { phase=Phase.END; result="我方胜"; }
    }

    private boolean allSunk(int[][] grid){
        for (int y=0;y<N;y++) for (int x=0;x<N;x++) if (grid[x][y]==1) return false;
        return true;
    }
    private boolean allSunkOpp(){
        int need=0,hit=0; for(int l: fleet) need+=l;
        for (int y=0;y<N;y++) for (int x=0;x<N;x++) if (opp[x][y]==2) hit++;
        return hit>=need;
    }

    @Override public boolean play(int x,int y){ return phase==Phase.PLAY && fireAtOpponent(x,y); }

    public Point lastShotMine(){ return lastShotMine; }
    public Point lastShotOpp(){ return lastShotOpp; }
}

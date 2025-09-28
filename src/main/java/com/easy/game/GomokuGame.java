package com.easy.game;

public class GomokuGame implements Game {
    private final int n = 15;
    private final int[][] b = new int[n][n];
    private boolean myTurn = true;
    private int current = 1; // 1 black, 2 white
    private boolean finished = false;
    private String result = "";
    private boolean hostStarts = true;

    @Override public int size(){ return n; }
    @Override public int currentPlayer(){ return current; }
    @Override public boolean myTurn(){ return myTurn; }
    @Override public void setMyTurn(boolean my){ myTurn = my; }
    @Override public GameType type(){ return GameType.GOMOKU; }

    @Override public void reset(boolean hostStarts){
        for (int y=0;y<n;y++) for (int x=0;x<n;x++) b[y][x]=0;
        this.hostStarts = hostStarts;
        this.current = 1;
        this.finished = false;
        this.result = "";
        this.myTurn = hostStarts;
    }

    @Override public boolean play(int x, int y){
        if (finished) return false;
        if (x<0 || x>=n || y<0 || y>=n) return false;
        if (b[y][x]!=0) return false;
        b[y][x] = current;
        // win check
        if (isFive(x,y,current)) {
            finished = true;
            result = (current==1? "黑子":"白子") + "胜";
        } else {
            current = 3 - current;
            myTurn = !myTurn;
        }
        return true;
    }

    private boolean isFive(int x, int y, int c){
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
        for (int[] d: dirs){
            int cnt=1;
            for(int s=1;s<5;s++){ int nx=x+s*d[0], ny=y+s*d[1]; if (in(nx,ny)&&b[ny][nx]==c) cnt++; else break; }
            for(int s=1;s<5;s++){ int nx=x-s*d[0], ny=y-s*d[1]; if (in(nx,ny)&&b[ny][nx]==c) cnt++; else break; }
            if (cnt>=5) return true;
        }
        return false;
    }
    private boolean in(int x,int y){ return x>=0 && x<n && y>=0 && y<n; }

    @Override public int get(int x, int y){ return b[y][x]; }
    @Override public boolean isFinished(){ return finished; }
    @Override public String resultText(){ return result.isEmpty()? "进行中": result; }
}

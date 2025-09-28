package com.easy.game;

public class ReversiGame implements Game {
    private final int n = 8;
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
    @Override public GameType type(){ return GameType.REVERSI; }

    @Override public void reset(boolean hostStarts){
        for (int y=0;y<n;y++) for (int x=0;x<n;x++) b[y][x]=0;
        // initial four stones
        b[3][3] = 2; b[4][4] = 2;
        b[3][4] = 1; b[4][3] = 1;
        this.hostStarts = hostStarts;
        this.current = 1;
        this.finished = false;
        this.result = "";
        this.myTurn = hostStarts;
    }

    @Override public boolean play(int x, int y){
        if (finished) return false;
        if (x<0||x>=n||y<0||y>=n) return false;
        if (b[y][x]!=0) return false;
        int flipped = flipCount(x,y,current,false);
        if (flipped<=0) return false;
        // apply
        flipCount(x,y,current,true);
        b[y][x]=current;
        // next
        current = 3-current;
        myTurn = !myTurn;
        // if opponent has no moves, maybe pass; if both no moves -> end
        boolean oppHas = hasAnyMove(current);
        if (!oppHas){
            current = 3-current; // pass back
            myTurn = !myTurn;
            if (!hasAnyMove(current)) finishByScore();
        }
        return true;
    }

    private boolean hasAnyMove(int c){
        for(int y=0;y<n;y++) for(int x=0;x<n;x++) if (b[y][x]==0 && flipCount(x,y,c,false)>0) return true;
        return false;
    }

    private void finishByScore(){
        int black=0,white=0;
        for(int y=0;y<n;y++) for(int x=0;x<n;x++){ if(b[y][x]==1) black++; else if(b[y][x]==2) white++; }
        finished=true;
        if (black>white) result="黑子胜 ("+black+":"+white+")";
        else if (white>black) result="白子胜 ("+white+":"+black+")";
        else result="平局 ("+black+":"+white+")";
    }

    // count flips in 8 dirs; if apply=true then perform flips
    private int flipCount(int x,int y,int c, boolean apply){
        int total=0; int[][] dirs={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d: dirs){
            int i=1; int cnt=0;
            while(true){
                int nx=x+i*d[0], ny=y+i*d[1];
                if (nx<0||nx>=n||ny<0||ny>=n) { cnt=0; break; }
                int v=b[ny][nx];
                if (v==0){ cnt=0; break; }
                if (v==c){ break; }
                cnt++; i++;
            }
            // now check if ended with own piece and had at least one opponent piece
            int endx=x+i*d[0], endy=y+i*d[1];
            if (cnt>0 && endx>=0&&endx<n&&endy>=0&&endy<n && b[endy][endx]==c){
                total+=cnt;
                if (apply){
                    for (int k=1;k<=cnt;k++){
                        int nx=x+k*d[0], ny=y+k*d[1];
                        b[ny][nx]=c;
                    }
                }
            }
        }
        return total;
    }

    @Override public int get(int x, int y){ return b[y][x]; }
    @Override public boolean isFinished(){ return finished; }
    @Override public String resultText(){ return result.isEmpty()? "进行中": result; }
}

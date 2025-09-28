package com.easy.game;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.easy.game.Game;
import com.easy.game.GameType;

public class BattleshipGame implements Game {

    public enum Phase { SETUP, PLAY, END }

    private final int N = 10;
    private final int[][] own = new int[N][N];   // 0 空；>0 舰 id
    private final int[][] hitOnMe = new int[N][N]; // -1=miss, 1=hit

    private final int[][] enemyFog = new int[N][N]; // 我对敌盘的已打标记：-1 miss, 1 hit
    private final List<Integer> fleet = List.of(5,4,3,3,2);
    private final int[] placed = new int[fleet.size()]; // 0=未放

    private boolean myTurn = true;
    private boolean finished = false;
    private String  result = "";

    private Phase phase = Phase.SETUP;
    private long setupDeadline = System.currentTimeMillis() + 30_000L; // 30秒布阵

    @Override public GameType type(){ return GameType.BATTLE; }
    @Override public int size(){ return N; }
    @Override public boolean myTurn(){ return myTurn && !finished && phase==Phase.PLAY; }
    @Override public void setMyTurn(boolean v){ myTurn = v; }
    @Override public int currentPlayer(){ return myTurn?1:2; }
    @Override public boolean isFinished(){ return finished; }
    @Override public String resultText(){ return result; }

    @Override
    public void reset(boolean iStart){
        for (int y=0;y<N;y++) for (int x=0;x<N;x++){
            own[x][y]=0; hitOnMe[x][y]=0; enemyFog[x][y]=0;
        }
        for (int i=0;i<placed.length;i++) placed[i]=0;
        myTurn = iStart;
        finished=false; result="";
        phase = Phase.SETUP;
        setupDeadline = System.currentTimeMillis()+30_000L;
    }

    public Phase phase(){ 
        if (phase==Phase.SETUP && System.currentTimeMillis()>setupDeadline) {
            autoPlaceAll(); phase = Phase.PLAY;
        }
        return phase; 
    }

    public boolean onOwnBoard(int y){
        // BoardCanvas 将棋盘上下拆成两半，这里逻辑由画板传参控制
        return true; // 画板负责约束
    }
    public boolean onEnemyBoard(int y){
        return true; // 画板负责约束
    }

    /** 布置/旋转船（右键切换方向，左键放置） */
    public boolean placeOrRotateAt(int x,int y, boolean vertical){
        if (phase()!=Phase.SETUP) return false;
        int idx = nextUnplacedIndex();
        if (idx<0) return true; // 都放好了
        int len = fleet.get(idx);
        if (!canPlace(x,y,len,vertical)) return false;
        int id = idx+1;
        for (int i=0;i<len;i++){
            int xx = x+(vertical?0:i);
            int yy = y+(vertical?i:0);
            own[xx][yy] = id;
        }
        placed[idx] = 1;
        if (nextUnplacedIndex()<0) phase = Phase.PLAY; // 两个人各自 Ready 的逻辑交由网络层一起判定，这里单机直接进入 PLAY
        return true;
    }

    public boolean canPlace(int x,int y,int len, boolean vertical){
        if (vertical){
            if (y+len> N) return false;
            for (int i=0;i<len;i++) if (own[x][y+i]!=0) return false;
        }else{
            if (x+len> N) return false;
            for (int i=0;i<len;i++) if (own[x+i][y]!=0) return false;
        }
        return true;
    }
    private int nextUnplacedIndex(){
        for (int i=0;i<placed.length;i++) if (placed[i]==0) return i;
        return -1;
    }

    private void autoPlaceAll(){
        Random r = new Random();
        for (int i=0;i<placed.length;i++) if (placed[i]==0){
            int len = fleet.get(i);
            while (true){
                boolean vertical = r.nextBoolean();
                int x = r.nextInt(N - (vertical?1:len) + 1);
                int y = r.nextInt(N - (vertical?len:1) + 1);
                if (canPlace(x,y,len,vertical)){
                    int id = i+1;
                    for (int k=0;k<len;k++){
                        int xx = x+(vertical?0:k);
                        int yy = y+(vertical?k:0);
                        own[xx][yy]=id;
                    }
                    placed[i]=1;
                    break;
                }
            }
        }
    }

    // —— 开火（我打对方盘，只在 fog 上做标记；命中与否由网络回执决定：这里给个本地即时反馈版）——
    public enum FireResult { MISS, HIT, WIN }

    public FireResult fireAtEnemy(int x,int y){
        if (enemyFog[x][y]!=0) return FireResult.MISS; // 已打过
        // 本地演示版：随机 30% 命中
        boolean hit = new Random().nextInt(100) < 30;
        enemyFog[x][y] = hit? 1 : -1;
        myTurn = false;
        return hit? FireResult.HIT : FireResult.MISS;
    }

    /** 敌方打我（server/client 收到对方的 FIRE 后调用） */
    public void applyEnemyFire(int x,int y){
        if (hitOnMe[x][y]!=0) return;
        hitOnMe[x][y] = (own[x][y]>0? 1 : -1);
        // 判输
        if (allMyShipsDown()){
            finished=true; result="你被击沉全部舰船";
        }else{
            myTurn = true;
        }
    }

    private boolean allMyShipsDown(){
        // 只要 own 上的舰格全部对应 hitOnMe==1
        outer:
        for (int y=0;y<N;y++) for (int x=0;x<N;x++){
            if (own[x][y]>0 && hitOnMe[x][y]!=1) return false;
        }
        return true;
    }

    // ====== UI 绘制（由 BoardCanvas 调用） ======

    public void paintBoard(Graphics g, int x0, int y0, int cw, int ch, boolean mine, int hoverX, int hoverY, boolean rotate){
        int n = N;
        // 背景已由画板画，这里仅画内容
        if (mine){
            // 显示己方船体 & 对手命中/落空
            g.setColor(new Color(52,152,219,140));
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                if (own[xx][yy]>0){
                    g.fillRect(x0+xx*cw+1, y0+yy*ch+1, cw-2, ch-2);
                }
            }
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                if (hitOnMe[xx][yy]==-1){
                    g.setColor(Color.GRAY);
                    g.drawLine(x0+xx*cw+3, y0+yy*ch+3, x0+xx*cw+cw-3, y0+yy*ch+ch-3);
                    g.drawLine(x0+xx*cw+3, y0+yy*ch+ch-3, x0+xx*cw+cw-3, y0+yy*ch+3);
                }else if (hitOnMe[xx][yy]==1){
                    g.setColor(new Color(231,76,60));
                    g.fillOval(x0+xx*cw+5, y0+yy*ch+5, cw-10, ch-10);
                }
            }
        }else{
            // 敌盘：只显示我打过的痕迹
            for (int yy=0; yy<n; yy++) for (int xx=0; xx<n; xx++){
                if (enemyFog[xx][yy]==-1){
                    g.setColor(Color.GRAY);
                    g.drawLine(x0+xx*cw+3, y0+yy*ch+3, x0+xx*cw+cw-3, y0+yy*ch+ch-3);
                    g.drawLine(x0+xx*cw+3, y0+yy*ch+ch-3, x0+xx*cw+cw-3, y0+yy*ch+3);
                }else if (enemyFog[xx][yy]==1){
                    g.setColor(new Color(46,204,113));
                    g.fillOval(x0+xx*cw+5, y0+yy*ch+5, cw-10, ch-10);
                }
            }
            // Hover 光标
            if (hoverX>=0 && hoverY>=0){
                g.setColor(new Color(0,0,0,40));
                g.fillRect(x0+hoverX*cw+1, y0+hoverY*ch+1, cw-2, ch-2);
            }
        }
    }

    @Override public boolean play(int x,int y){ return false; }
    @Override
    public int get(int x, int y) {
        // 海战棋不走通用的单盘渲染，这里仅为满足 Game 接口
        return 0;
    }
}

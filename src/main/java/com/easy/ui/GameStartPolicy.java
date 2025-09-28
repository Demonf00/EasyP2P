package com.easy.ui;

/** 全局先后手协调：严格交替（host, client, host, ...） */
public final class GameStartPolicy {
    private static volatile boolean nextStartHost = true; // 程序初始：房主先手

    private GameStartPolicy(){}

    /** 当前这一局应由谁先手：true=host, false=client */
    public static boolean nextStartIsHost(){ return nextStartHost; }

    /** 在“开始一局”时调用，锁定当前先手并翻转到下一局 */
    public static void consumeAndFlip(){
        nextStartHost = !nextStartHost;
    }

    /** 强制设置（很少用） */
    public static void setNextStartHost(boolean v){ nextStartHost = v; }
}

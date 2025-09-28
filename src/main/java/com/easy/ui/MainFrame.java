package com.easy.ui;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame implements ConsoleSink {
    private final ConsolePanel console = new ConsolePanel();
    private final SidebarPanel sidebar = new SidebarPanel(this);
    private BoardCanvas board;

    public MainFrame(){
        super("easy-p2p 对战");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        // board needs current MoveSender
        board = 
new BoardCanvas(new com.easy.ui.MoveSender(){
    @Override public void sendMove(int x,int y,int turn,String hash) throws java.io.IOException {
        MoveSender sender = sidebar.getCurrentSender();
        if (sender == null) throw new java.io.IOException("尚未建立连接");
        sender.sendMove(x,y,turn,hash);
    }
}, this);

        sidebar.setBoard(board);

        add(sidebar, BorderLayout.WEST);
        add(board, BorderLayout.CENTER);
        add(console, BorderLayout.SOUTH);
        setSize(1100, 780);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    @Override
    public void println(String s){
        console.println(s);
    }
}

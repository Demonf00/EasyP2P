package com.easy.ui;

import javax.swing.*;
import java.awt.*;

public 
class ConsolePanel extends JPanel implements ConsoleSink {
    private final JTextArea area = new JTextArea(10, 80); // ~10 rows by default

    public ConsolePanel() {
        setLayout(new BorderLayout());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    @Override
    public void println(String s) {
        SwingUtilities.invokeLater(() -> {
            area.append(s + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }
}

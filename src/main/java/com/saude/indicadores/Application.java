package com.saude.indicadores;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.saude.indicadores.ui.MainFrame;

/**
 * Ponto de entrada principal da aplicação Saúde · Indicadores.
 */
public class Application {

    public static void main(String[] args) {
        configurarLogging();

        // Look and Feel nativo do SO (Windows, macOS ou GTK)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Habilitar anti-aliasing de texto em toda a aplicação
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Iniciar na Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static void configurarLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        // Remove handlers padrão
        for (Handler h : rootLogger.getHandlers()) rootLogger.removeHandler(h);

        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.INFO);
        ch.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord r) {
                return String.format("[%s] %s: %s%n",
                    r.getLevel(), r.getLoggerName().replaceAll(".*\\.", ""), r.getMessage());
            }
        });
        rootLogger.addHandler(ch);
    }
}

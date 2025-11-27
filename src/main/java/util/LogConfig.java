package util;

import java.io.IOException;
import java.util.logging.*;

public class LogConfig {

    private static boolean initialized = false;

    public static void configure() {
        if (initialized) return;
        initialized = true;

        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("logs"));

            String logFile = "logs/teammate.log";

            FileHandler fileHandler = new FileHandler(logFile, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);

            Logger root = Logger.getLogger("");
            root.setLevel(Level.ALL);

            for (Handler h : root.getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    root.removeHandler(h);
                }
            }

            root.addHandler(fileHandler);

            System.out.println("Logging configured. Log file: " + logFile);

        } catch (IOException e) {
            System.err.println("Logging configuration failed: " + e.getMessage());
        }
    }
}

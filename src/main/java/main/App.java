package main;

import ui.Menu;
import util.LogConfig;

public class App {
    public static void main(String[] args) {
        LogConfig.configure();
        new Menu().start();
    }
}
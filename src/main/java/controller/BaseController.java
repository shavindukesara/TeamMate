package controller;

import java.util.Scanner;

public abstract class BaseController {
    protected final Scanner scanner;

    protected BaseController(Scanner scanner) {
        this.scanner = scanner;
    }

    protected String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }
}

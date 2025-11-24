package controller;

import service.AuthService;

import java.util.Scanner;

public class AuthController {
    private final AuthService authService;
    private final Scanner scanner;
    private String loggedInUser = null;

    public AuthController(Scanner scanner) {
        this.scanner = scanner;
        this.authService = new AuthService();
    }
    public boolean launchAuthFlow() {
        while (true) {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("           TEAMMATE - TEAM FORMATION SYSTEM");
            System.out.println("              University Gaming Club");
            System.out.println("=".repeat(70));
            System.out.println("\nAuthentication:");
            System.out.println("1) Login");
            System.out.println("2) Register");
            System.out.println("3) Continue as Guest");
            System.out.print("Choose: ");
            String opt = scanner.nextLine().trim();

            switch (opt) {
                case "1" -> {
                    try {
                        if (login()) return true;
                    } catch (Exception e) {
                        System.err.println("Login error: " + e.getMessage());
                    }
                }
                case "2" -> {
                    try {
                        if (registerAndLogin()) return true;
                    } catch (Exception e) {
                        System.err.println("Register error: " + e.getMessage());
                    }
                }
                case "3" -> {
                    return false;
                }
                default -> System.out.println("Invalid selection. Please press 1, 2 or 3.");
            }
        }
    }

    private boolean login() throws Exception {
        System.out.print("Username: ");
        String u = scanner.nextLine().trim();

        System.out.print("Password: ");
        String p = (System.console() != null)
                ? new String(System.console().readPassword())
                : scanner.nextLine();

        boolean ok = authService.login(u, p);

        if (!ok) {
            System.out.println("\nUser not found or password incorrect.");
            System.out.println("1) Try Again");
            System.out.println("2) Continue as Guest");
            System.out.print("Your choice: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                return login();
            } else {
                return false;
            }
        }

        loggedInUser = u;
        System.out.println("Login successful.");
        return true;
    }

    private boolean registerAndLogin() throws Exception {
        System.out.print("Enter a new username: ");
        String u = scanner.nextLine().trim();
        System.out.print("Enter a new password: ");
        String p = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();

        boolean created = authService.register(u, p);
        if (!created) {
            System.out.println("Registration failed (username may already exist or input invalid).");
            return false;
        }

        System.out.println("Registered successfully. Please login with your new credentials.");
        return login();
    }


    public String getLoggedInUser() {
        return loggedInUser;
    }
}

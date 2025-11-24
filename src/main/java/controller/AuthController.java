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
        System.out.println("\nAuthentication: 1) Login  2) Register  3) Guest");
        System.out.print("Choose: ");
        String opt = scanner.nextLine().trim();
        try {
            return switch (opt) {
                case "1" -> login();
                case "2" -> registerAndLogin();
                default -> false;
            };
        } catch (Exception e) {
            System.err.println("Auth error: " + e.getMessage());
            return false;
        }
    }

    private boolean login() throws Exception {
        System.out.print("Username: ");
        String u = scanner.nextLine().trim();
        System.out.print("Password: ");
        String p = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();
        boolean ok = authService.login(u, p);
        if (ok) loggedInUser = u;
        return ok;
    }

    private boolean registerAndLogin() throws Exception {
        System.out.print("Choose username: ");
        String u = scanner.nextLine().trim();
        System.out.print("Choose password: ");
        String p = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();
        boolean created = authService.register(u, p);
        if (!created) return false;
        loggedInUser = u;
        return true;
    }

    public String getLoggedInUser() {
        return loggedInUser;
    }
}

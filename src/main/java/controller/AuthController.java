package controller;

import service.AuthService;

import java.util.Scanner;

public class AuthController extends BaseController {
    private final AuthService authService;
    private String loggedInUser = null;

    public AuthController(Scanner scanner) {
        super(scanner);
        this.authService = new AuthService();
    }

    public boolean launchAuthFlow() {
        while (true) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("           TEAMMATE - TEAM FORMATION SYSTEM");
            System.out.println("                University Gaming Club");
            System.out.println("=".repeat(55));
            System.out.println("\nAuthentication");
            System.out.println("\n1 - Login as an existing user");
            System.out.println("2 - Register as a new user");
            System.out.println("3 - Continue as Guest");
            System.out.println("\n" + "=".repeat(55));
            System.out.print("Your choice: ");
            String opt = scanner.nextLine().trim();

            switch (opt) {
                case "1" -> {
                    try {
                        boolean loginResult = login();
                        if (loginResult) return true;
                        return false;
                    } catch (Exception e) {
                        System.err.println("Login error: " + e.getMessage());
                        System.out.println("Continuing as guest...");
                        return false;
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
        System.out.println("\n" + "=".repeat(55));
        System.out.print("Enter Username: ");
        String u = scanner.nextLine().trim();

        System.out.print("Enter Password: ");
        String p = (System.console() != null)
                ? new String(System.console().readPassword())
                : scanner.nextLine();

        boolean ok = authService.login(u, p);

        if (!ok) {
            System.out.println("=".repeat(55));
            System.out.println("\nUser not found or password incorrect.");
            System.out.println("1 - Try Again");
            System.out.println("2 - Register as a new user");
            System.out.println("3 - Continue as Guest");
            System.out.print("\nYour choice: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                return login();
            } else if ("2".equals(choice)) {
                return registerAndLogin();
            }
            else {
                return false;
            }
        }

        loggedInUser = u;
        System.out.println("\n" + "=".repeat(55));
        System.out.println("Login successful.");
        return true;
    }

    private boolean registerAndLogin() throws Exception {
        System.out.println("\n" + "=".repeat(55));
        System.out.print("Enter a new username: ");
        String u = scanner.nextLine().trim();
        System.out.print("Enter a new password: ");
        String p = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();  // Use inherited scanner

        boolean created = authService.register(u, p);
        if (!created) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("Registration failed. \n     Username may already exist or input invalid.");
            return registerAndLogin();
        }

        System.out.println("\n" + "=".repeat(55));
        System.out.println("Registered successfully. \nPlease login with your new credentials.");
        return login();
    }

    public String getLoggedInUser() {
        return loggedInUser;
    }
}
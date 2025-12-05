package controller;

import service.AuthService;
import java.util.Scanner;

public class AuthController extends BaseController {
    private final AuthService authService;
    private String loggedInUser = null; // Stores the username of the currently logged-in user

    public AuthController(Scanner scanner) {
        super(scanner);
        this.authService = new AuthService();
    }

    // Main authentication flow that presents options to user
    public boolean launchAuthFlow() {
        while (true) {
            // Display the welcome screen with system title and authentication options
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
                    // Handle login attempt for existing user
                    try {
                        boolean loginResult = login();
                        if (loginResult) return true; // Return true if login successful
                        return false; // Return false if login unsuccessful
                    } catch (Exception e) {
                        System.err.println("Login error: " + e.getMessage());
                        System.out.println("Continuing as guest...");
                        return false; // Fall back to guest mode on error
                    }
                }
                case "2" -> {
                    // Handle registration for new user
                    try {
                        if (registerAndLogin()) return true; // Return true if registration and login successful
                    } catch (Exception e) {
                        System.err.println("Register error: " + e.getMessage());
                    }
                }
                case "3" -> {
                    // User chooses to continue without authentication
                    return false;
                }
                default -> System.out.println("Invalid selection. Please press 1, 2 or 3.");
            }
        }
    }

    // Handles the login process for existing users
    private boolean login() throws Exception {
        System.out.println("\n" + "=".repeat(55));
        System.out.print("Enter Username: ");
        String u = scanner.nextLine().trim();

        System.out.print("Enter Password: ");
        // Use console for password input if available (hides password), otherwise use scanner
        String p = (System.console() != null)
                ? new String(System.console().readPassword())
                : scanner.nextLine();

        // Attempt to authenticate user with provided credentials
        boolean ok = authService.login(u, p);

        if (!ok) {
            // Display options when login fails
            System.out.println("=".repeat(55));
            System.out.println("\nUser not found or password incorrect.");
            System.out.println("1 - Try Again");
            System.out.println("2 - Register as a new user");
            System.out.println("3 - Continue as Guest");
            System.out.print("\nYour choice: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                return login(); // Retry login
            } else if ("2".equals(choice)) {
                return registerAndLogin(); // Switch to registration flow
            }
            else {
                return false; // Continue as guest
            }
        }

        // Store the logged-in username and display success message
        loggedInUser = u;
        System.out.println("\n" + "=".repeat(55));
        System.out.println("Login successful.");
        return true;
    }

    // Handles both registration and immediate login for new users
    private boolean registerAndLogin() throws Exception {
        System.out.println("\n" + "=".repeat(55));
        System.out.print("Enter a new username: ");
        String u = scanner.nextLine().trim();
        System.out.print("Enter a new password: ");
        // Use console for password input if available, otherwise use scanner
        String p = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();

        // Attempt to create new user account
        boolean created = authService.register(u, p);
        if (!created) {
            // Handle registration failure
            System.out.println("\n" + "=".repeat(55));
            System.out.println("Registration failed. \n     Username may already exist or input invalid.");
            return registerAndLogin(); // Retry registration
        }

        // Registration successful, prompt user to login
        System.out.println("\n" + "=".repeat(55));
        System.out.println("Registered successfully. \nPlease login with your new credentials.");
        return login(); // Automatically proceed to login with new credentials
    }

    // Returns the username of the currently logged-in user (null if guest)
    public String getLoggedInUser() {
        return loggedInUser;
    }
}
package ui;

import controller.AdminController;
import controller.AuthController;
import controller.ParticipantAuthController;
import controller.ParticipantController;
import repository.CsvParticipantRepository;
import repository.ParticipantRepository;
import service.MatchingAlgorithm;
import service.TeamFormationStrategy;

import java.util.Scanner;

public class Menu {
    private final Scanner scanner = new Scanner(System.in);
    private final ParticipantRepository repository = new CsvParticipantRepository();
    private final TeamFormationStrategy strategy = new MatchingAlgorithm();

    public void start() {
        while (true) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("            TEAMMATE - ROLE SELECTION");
            System.out.println("=".repeat(55));
            System.out.println("  1 - Organizer (Admin)");
            System.out.println("  2 - Participant");
            System.out.println("  0 - Exit");
            System.out.print("\n  Choose role: ");
            String roleChoice = scanner.nextLine().trim();

            if ("0".equals(roleChoice)) {
                System.out.println("\n" + "=".repeat(55));
                System.out.println("            Thank you for using TeamMate!");
                System.out.println("=".repeat(55));
                return;
            }

            if ("1".equals(roleChoice)) {
                // Admin path
                AuthController auth = new AuthController(scanner);
                boolean loggedIn = auth.launchAuthFlow();
                if (!loggedIn) {
                    System.out.println("            Returning to role selection.");
                    continue;
                }
                System.out.println("\n            ✓ Logged in as admin: " + auth.getLoggedInUser());
                AdminController adminController = new AdminController(scanner, repository, strategy);
                runAdminMenu(adminController);

            } else if ("2".equals(roleChoice)) {
                // Participant path
                ParticipantAuthController pac = new ParticipantAuthController(scanner, repository, strategy);
                ParticipantController participantController = pac.launchParticipantSession();
                if (participantController == null) {
                    System.out.println("            Returning to role selection.");
                    continue;
                }
                runParticipantMenu(participantController);

            } else {
                System.out.println("            Invalid choice. Please enter 0, 1 or 2.");
            }
        }
    }

    private void runAdminMenu(AdminController adminController) {
        boolean running = true;
        while (running) {
            printAdminMenu();
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> adminController.loadParticipants(false);
                    case "2" -> adminController.loadParticipants(true);
                    case "3" -> adminController.displayParticipantStats();
                    case "4" -> adminController.formTeams();
                    case "5" -> adminController.displayTeams();
                    case "6" -> adminController.exportTeams();
                    case "7" -> adminController.analyzeTeams();
                    case "8" -> {
                        System.out.println("\n            Logging out admin...");
                        running = false;
                    }
                    case "9" -> {
                        // Rebalance (quick N attempts)
                        System.out.print("            Enter attempts for rebalance (or press Enter for default 15): ");
                        String s = scanner.nextLine().trim();
                        int attempts = s.isEmpty() ? 15 : Integer.parseInt(s);
                        System.out.print("            Random mode? (y/n, default y): ");
                        String r = scanner.nextLine().trim();
                        boolean randomMode = r.isEmpty() || r.equalsIgnoreCase("y");
                        adminController.rebalanceTeams(attempts, randomMode);
                    }
                    case "10" -> {
                        // Super-balance
                        System.out.print("            Enter maxTotalAttempts (default 200): ");
                        String a = scanner.nextLine().trim();
                        int max = a.isEmpty() ? 200 : Integer.parseInt(a);
                        System.out.print("            Enter batch size (default 15): ");
                        String b = scanner.nextLine().trim();
                        int batch = b.isEmpty() ? 15 : Integer.parseInt(b);
                        System.out.print("            Random mode? (y/n, default y): ");
                        String r2 = scanner.nextLine().trim();
                        boolean randomMode2 = r2.isEmpty() || r2.equalsIgnoreCase("y");
                        adminController.superBalance(max, batch, randomMode2);
                    }
                    case "0" -> {
                        System.out.println("\n" + "=".repeat(55));
                        System.out.println("            Exiting TeamMate System...");
                        System.out.println("=".repeat(55));
                        System.exit(0);
                    }
                    default -> System.out.println("            Invalid choice. Please try again.");
                }
            } catch (NumberFormatException e) {
                System.err.println("            Invalid number format. Please try again.");
            } catch (Exception e) {
                System.err.println("            Error: " + e.getMessage());
            }
        }
    }

    private void runParticipantMenu(ParticipantController participantController) {
        boolean running = true;
        while (running) {
            printParticipantMenu();
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> participantController.viewMyProfile();
                    case "2" -> participantController.viewMyTeam();
                    case "3" -> participantController.displayTeams();
                    case "4" -> participantController.refreshTeams();
                    case "5" -> participantController.displayParticipantStats();
                    case "8" -> {
                        System.out.println("\n            Logging out participant...");
                        running = false;
                    }
                    case "0" -> {
                        System.out.println("\n" + "=".repeat(55));
                        System.out.println("            Exiting TeamMate System...");
                        System.out.println("=".repeat(55));
                        System.exit(0);
                    }
                    default -> System.out.println("            Invalid choice. Please try again.");
                }
            } catch (Exception e) {
                System.err.println("            Error: " + e.getMessage());
            }
        }
    }

    private void printAdminMenu() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("                      ADMIN MENU");
        System.out.println("=".repeat(55));
        System.out.println("  1  - Load Participants (Standard)");
        System.out.println("  2  - Load Participants (Concurrent)");
        System.out.println("  3  - Display Participant Statistics");
        System.out.println("  4  - Form Teams");
        System.out.println("  5  - Display Formed Teams");
        System.out.println("  6  - Export Teams");
        System.out.println("  7  - Analyze Team Balance");
        System.out.println("  8  - Logout");
        System.out.println("  9  - Rebalance (N attempts)");
        System.out.println("  10 - Super-balance (batch attempts)");
        System.out.println("  0  - Exit");
        System.out.println("=".repeat(55));
        System.out.print("  Enter Your Choice: ");
    }

    private void printParticipantMenu() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("                  PARTICIPANT MENU");
        System.out.println("=".repeat(55));
        System.out.println("  1 - View My Profile");
        System.out.println("  2 - View My Team");
        System.out.println("  3 - View All Formed Teams");
        System.out.println("  4 - Refresh Teams (reload from system)");
        System.out.println("  5 - Participant Statistics");
        System.out.println("  8 - Logout");
        System.out.println("  0 - Exit");
        System.out.println("=".repeat(55));
        System.out.print("  Enter Your Choice: ");
    }
}
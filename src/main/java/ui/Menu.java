package ui;

import controller.AuthController;
import controller.ParticipantController;
import java.util.Scanner;

public class Menu {
    private final Scanner scanner = new Scanner(System.in);
    private final AuthController authController = new AuthController(scanner);
    private final ParticipantController participantController = new ParticipantController(scanner);

    public void start() {
        if (authController.launchAuthFlow()) {
            System.out.println("Logged in as: " + authController.getLoggedInUser());
        } else {
            System.out.println("\n            Continuing as guest...");
        }

        boolean running = true;
        while (running) {
            showMain();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> participantController.loadParticipants(false);
                case "2" -> participantController.loadParticipants(true);
                case "3" -> participantController.displayParticipantStats();
                case "4" -> participantController.formTeams();
                case "5" -> participantController.displayTeams();
                case "6" -> participantController.exportTeams();
                case "7" -> participantController.analyzeTeams();
                case "8" -> participantController.addNewParticipant();
                case "0" -> running = false;
                default -> System.out.println("Invalid choice.");
            }
        }
        System.out.println("=".repeat(55));
        System.out.println("             Thank you for using TeamMate.");
    }

    private void showMain() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("                      MAIN MENU");
        System.out.println("=".repeat(55));
        System.out.println("            1 - Load Participants (Standard)");
        System.out.println("            2 - Load Participants (Concurrent)");
        System.out.println("            3 - Display Participant Statistics");
        System.out.println("            4 - Form Teams");
        System.out.println("            5 - Display Formed Teams");
        System.out.println("            6 - Export Teams");
        System.out.println("            7 - Analyze Team Balance");
        System.out.println("            8 - Add New Participant");
        System.out.println("            0 - Exit");
        System.out.print("\n            Enter Your Choice: ");
    }
}

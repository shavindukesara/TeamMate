package ui;

import controller.AuthController;
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
            AuthController auth = new AuthController(scanner);
            boolean authenticated = auth.launchAuthFlow();   // login/register/guest
            String user = auth.getLoggedInUser();

            if (authenticated) {
                System.out.println("\nLogged in as: " + user);
            } else {
                System.out.println("\n            Continuing as guest...");
            }

            ParticipantController participants = new ParticipantController(scanner, repository, strategy);

            boolean inMainMenu = true;
            while (inMainMenu) {
                printMainMenu();
                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1" -> participants.loadParticipants(false);
                    case "2" -> participants.loadParticipants(true);
                    case "3" -> participants.addNewParticipant();
                    case "4" -> participants.formTeams();
                    case "5" -> participants.displayParticipantStats();
                    case "6" -> participants.displayTeams();
                    case "7" -> participants.exportTeams();
                    case "8" -> participants.analyzeTeams();
                    case "9" -> {
                        System.out.println("\n             Logging out...");
                        inMainMenu = false;
                    }
                    case "0" -> {
                        System.out.println("\n" + "=".repeat(55));
                        System.out.println("             Thank you for using TeamMate.");
                        return;
                    }
                    default -> System.out.println("             Invalid choice. Please enter 0–9.");
                }
            }
        }
    }

    private void printMainMenu() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("                      MAIN MENU");
        System.out.println("=".repeat(55));
        System.out.println("            1 - Load Participants (Standard)");
        System.out.println("            2 - Load Participants (Concurrent)");
        System.out.println("            3 - Add New Participant");
        System.out.println("            4 - Form Teams");
        System.out.println("            5 - Display Participant Statistics");
        System.out.println("            6 - Display Formed Teams");
        System.out.println("            7 - Export Teams");
        System.out.println("            8 - Analyze Team Balance");
        System.out.println("            9 - Logout");
        System.out.println("            0 - Exit");
        System.out.print("\n            Enter Your Choice: ");
    }
}

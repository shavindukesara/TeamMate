package main;

import model.*;
import service.*;
import util.*;
import concurrent.*;
import exception.*;
import java.util.*;
import java.util.logging.*;

public class ConsoleUI {
    private static final Logger LOGGER = Logger.getLogger(ConsoleUI.class.getName());
    private static final Scanner scanner = new Scanner(System.in);
    private List<Participant> participants;
    private List<Team> formedTeams;

    public void start() {
        setupLogging();
        displayWelcome();

        boolean running = true;
        while (running) {
            displayMenu();
            int choice = getMenuChoice();

            switch (choice) {
                case 1:
                    loadParticipants();
                    break;
                case 2:
                    loadParticipantsConcurrently();
                    break;
                case 3:
                    displayParticipantStats();
                    break;
                case 4:
                    formTeams();
                    break;
                case 5:
                    displayTeams();
                    break;
                case 6:
                    exportTeams();
                    break;
                case 7:
                    analyzeTeams();
                    break;
                case 0:
                    running = false;
                    System.out.println("\nThank you for using TeamMate!");
                    break;
                default:
                    System.out.println("\nInvalid choice. Please try again.");
            }
        }
    }

    private void setupLogging() {
        try {
            LogManager.getLogManager().reset();
            Logger rootLogger = Logger.getLogger("");

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.WARNING);
            rootLogger.addHandler(consoleHandler);

            FileHandler fileHandler = new FileHandler("logs/teammate.log", true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(fileHandler);

            rootLogger.setLevel(Level.ALL);
        } catch (Exception e) {
            System.err.println("Failed to setup logging: " + e.getMessage());
        }
    }

    private void displayWelcome() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("           TEAMMATE - TEAM FORMATION SYSTEM");
        System.out.println("              University Gaming Club");
        System.out.println("=".repeat(70));
    }

    private void displayMenu() {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("MAIN MENU:");
        System.out.println("-".repeat(70));
        System.out.println("1. Load Participants from CSV (Standard)");
        System.out.println("2. Load Participants from CSV (Concurrent Processing)");
        System.out.println("3. Display Participant Statistics");
        System.out.println("4. Form Teams");
        System.out.println("5. Display Formed Teams");
        System.out.println("6. Export Teams to CSV");
        System.out.println("7. Analyze Team Balance");
        System.out.println("0. Exit");
        System.out.println("-".repeat(70));
        System.out.print("Enter your choice: ");
    }

    private int getMenuChoice() {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void loadParticipants() {
        System.out.print("\nEnter CSV file path (or press Enter for default): ");
        String filePath = scanner.nextLine().trim();

        if (filePath.isEmpty()) {
            filePath = "data/participants_sample.csv";
        }

        try {
            long startTime = System.currentTimeMillis();
            participants = CSVHandler.loadParticipants(filePath);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("✓ Successfully loaded " + participants.size() +
                    " participants in " + duration + "ms");

        } catch (Exception e) {
            System.err.println("✗ Failed to load participants: " + e.getMessage());
            LOGGER.severe("Load error: " + e.getMessage());
        }
    }

    private void loadParticipantsConcurrently() {
        System.out.print("\nEnter CSV file path (or press Enter for default): ");
        String filePath = scanner.nextLine().trim();

        if (filePath.isEmpty()) {
            filePath = "data/participants_sample.csv";
        }

        try {
            long startTime = System.currentTimeMillis();
            participants = ConcurrentCSVHandler.loadParticipantsConcurrently(filePath);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("✓ Successfully loaded " + participants.size() +
                    " participants using concurrent processing in " +
                    duration + "ms");

        } catch (Exception e) {
            System.err.println("✗ Failed to load participants: " + e.getMessage());
            LOGGER.severe("Load error: " + e.getMessage());
        }
    }

    private void displayParticipantStats() {
        if (participants == null || participants.isEmpty()) {
            System.out.println("\n✗ No participants loaded. Please load data first.");
            return;
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("PARTICIPANT STATISTICS");
        System.out.println("=".repeat(70));

        // Count by personality type
        Map<PersonalityType, Long> byPersonality = new HashMap<>();
        for (PersonalityType type : PersonalityType.values()) {
            long count = participants.stream()
                    .filter(p -> p.getPersonalityType() == type)
                    .count();
            byPersonality.put(type, count);
        }

        System.out.println("\nBy Personality Type:");
        for (PersonalityType type : PersonalityType.values()) {
            System.out.printf("  %-10s: %3d participants\n",
                    type, byPersonality.get(type));
        }

        // Count by game
        Map<String, Long> byGame = new HashMap<>();
        participants.stream()
                .map(Participant::getPreferredGame)
                .forEach(game -> byGame.merge(game, 1L, Long::sum));

        System.out.println("\nBy Preferred Game:");
        byGame.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> System.out.printf("  %-15s: %3d participants\n",
                        entry.getKey(), entry.getValue()));

        // Average skill
        double avgSkill = participants.stream()
                .mapToInt(Participant::getSkillLevel)
                .average()
                .orElse(0.0);

        System.out.printf("\nAverage Skill Level: %.2f\n", avgSkill);
        System.out.println("=".repeat(70));
    }

    private void formTeams() {
        if (participants == null || participants.isEmpty()) {
            System.out.println("\n✗ No participants loaded. Please load data first.");
            return;
        }

        System.out.print("\nEnter desired team size (3-10): ");
        int teamSize;

        try {
            teamSize = Integer.parseInt(scanner.nextLine().trim());
            if (!ValidationUtil.validateTeamSize(teamSize, participants.size())) {
                System.out.println("✗ Invalid team size for " + participants.size() +
                        " participants");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("✗ Invalid input");
            return;
        }

        try {
            System.out.println("\nForming teams...");
            long startTime = System.currentTimeMillis();

            formedTeams = MatchingAlgorithm.matchParticipants(participants, teamSize);

            long duration = System.currentTimeMillis() - startTime;

            System.out.println("✓ Successfully formed " + formedTeams.size() +
                    " teams in " + duration + "ms");

        } catch (TeamFormationException e) {
            System.err.println("✗ Team formation failed: " + e.getMessage());
            LOGGER.severe("Formation error: " + e.getMessage());
        }
    }

    private void displayTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("\n✗ No teams formed. Please form teams first.");
            return;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("FORMED TEAMS");
        System.out.println("=".repeat(80));

        for (Team team : formedTeams) {
            System.out.println("\n" + team.getTeamName() + " (" + team.getTeamId() + ")");
            System.out.println("-".repeat(80));
            System.out.printf("Members: %d | Avg Skill: %.2f | Unique Roles: %d\n",
                    team.getCurrentSize(),
                    team.calculateAverageSkill(),
                    team.getUniqueRoleCount());

            System.out.println("\nMembers:");
            System.out.printf("  %-20s %-15s %-7s %-13s %-10s\n",
                    "Name", "Game", "Skill", "Role", "Type");
            System.out.println("  " + "-".repeat(75));

            for (Participant p : team.getMembers()) {
                System.out.printf("  %-20s %-15s %4d    %-13s %-10s\n",
                        p.getName(),
                        p.getPreferredGame(),
                        p.getSkillLevel(),
                        p.getPreferredRole(),
                        p.getPersonalityType());
            }
        }
        System.out.println("\n" + "=".repeat(80));
    }

    private void exportTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("\n✗ No teams to export. Please form teams first.");
            return;
        }

        System.out.print("\nEnter output file path (or press Enter for default): ");
        String filePath = scanner.nextLine().trim();

        if (filePath.isEmpty()) {
            filePath = "data/formed_teams.csv";
        }

        try {
            CSVHandler.saveTeams(formedTeams, filePath);
            System.out.println("✓ Teams exported successfully to: " + filePath);
        } catch (Exception e) {
            System.err.println("✗ Export failed: " + e.getMessage());
            LOGGER.severe("Export error: " + e.getMessage());
        }
    }

    private void analyzeTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("\n✗ No teams to analyze. Please form teams first.");
            return;
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEAM BALANCE ANALYSIS");
        System.out.println("=".repeat(70));

        // Skill balance
        double globalAvg = formedTeams.stream()
                .mapToDouble(Team::calculateAverageSkill)
                .average()
                .orElse(0.0);

        System.out.printf("\nGlobal Average Skill: %.2f\n", globalAvg);
        System.out.println("\nSkill Balance by Team:");

        for (Team team : formedTeams) {
            double teamAvg = team.calculateAverageSkill();
            double deviation = ((teamAvg - globalAvg) / globalAvg) * 100;
            String status = Math.abs(deviation) <= 15 ? "✓ Balanced" : "⚠ Imbalanced";

            System.out.printf("  %-12s: %.2f (%.1f%% deviation) %s\n",
                    team.getTeamId(), teamAvg, deviation, status);
        }

        // Role diversity
        System.out.println("\nRole Diversity:");
        for (Team team : formedTeams) {
            int uniqueRoles = team.getUniqueRoleCount();
            String status = uniqueRoles >= 3 ? "✓ Diverse" : "⚠ Limited";
            System.out.printf("  %-12s: %d unique roles %s\n",
                    team.getTeamId(), uniqueRoles, status);
        }

        // Personality mix
        System.out.println("\nPersonality Distribution:");
        for (Team team : formedTeams) {
            Map<PersonalityType, Integer> dist = team.getPersonalityDistribution();
            System.out.printf("  %-12s: L:%d B:%d T:%d\n",
                    team.getTeamId(),
                    dist.getOrDefault(PersonalityType.LEADER, 0),
                    dist.getOrDefault(PersonalityType.BALANCED, 0),
                    dist.getOrDefault(PersonalityType.THINKER, 0));
        }

        System.out.println("=".repeat(70));
    }

    public static void main(String[] args) {
        ConsoleUI ui = new ConsoleUI();
        ui.start();
    }
}

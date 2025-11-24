package main;

import model.*;
import service.AuthService;
import service.MatchingAlgorithm;
import service.Questionnaire;
import util.CSVHandler;
import util.ConcurrentCSVHandler;
import util.ValidationUtil;
import exception.*;
import java.util.*;
import java.util.logging.*;
import java.util.UUID;

public class ConsoleUI {
    private static final Logger LOGGER = Logger.getLogger(ConsoleUI.class.getName());
    private static final Scanner scanner = new Scanner(System.in);
    private List<Participant> participants;
    private List<Team> formedTeams;

    // New services
    private final AuthService authService = new AuthService();
    private final Questionnaire questionnaire = new Questionnaire();
    private String loggedInUser = null;

    public void start() {
        setupLogging();
        displayWelcome();

        // Authentication at startup
        try {
            boolean authed = showAuthMenu();
            if (!authed) {
                System.out.println("Continuing as guest (limited features may apply).");
            } else {
                System.out.println("Welcome, " + loggedInUser + "!");
            }
        } catch (Exception e) {
            System.err.println("Auth error: " + e.getMessage());
            LOGGER.warning("Auth error: " + e.getMessage());
        }

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
                case 8:
                    addNewParticipantFlow();
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

    private boolean showAuthMenu() throws Exception {
        System.out.println("\nAuthentication:");
        System.out.println("1) Login");
        System.out.println("2) Register");
        System.out.println("3) Continue as Guest");
        System.out.print("Choose: ");
        String opt = scanner.nextLine().trim();
        switch (opt) {
            case "1": return login();
            case "2": return register();
            default: return false;
        }
    }

    private boolean login() throws Exception {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();
        boolean ok = authService.login(username, password);
        if (ok) {
            loggedInUser = username;
            System.out.println("Login successful.");
            return true;
        } else {
            System.out.println("Login failed. Continue as guest? (y/N)");
            String c = scanner.nextLine().trim();
            return c.equalsIgnoreCase("y");
        }
    }

    private boolean register() throws Exception {
        System.out.print("Choose username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Choose password: ");
        String password = System.console() != null ? new String(System.console().readPassword()) : scanner.nextLine();
        boolean created = authService.register(username, password);
        if (created) {
            System.out.println("Account created. Please login.");
            return login();
        } else {
            System.out.println("Registration failed (username may exist or invalid).");
            return false;
        }
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
        System.out.println("8. Add New Participant (run questionnaire)");
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
                        entry.getKey(), entry.getValue())) ;

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
            double deviation = (globalAvg == 0.0) ? 0.0 : ((teamAvg - globalAvg) / globalAvg) * 100;
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

    // ========== NEW: Add a participant interactively ==========
    private void addNewParticipantFlow() {
        System.out.println("\nAdd New Participant");

        try {
            System.out.print("Full name: ");
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) {
                System.out.println("Name required.");
                return;
            }

            System.out.print("Email: ");
            String email = scanner.nextLine().trim();
            if (!ValidationUtil.validateEmail(email)) {
                System.out.println("Invalid email.");
                return;
            }

            System.out.print("Preferred game (e.g., Chess, DOTA 2): ");
            String game = scanner.nextLine().trim();
            if (!ValidationUtil.validateGame(game)) {
                System.out.println("Invalid or unsupported game.");
                return;
            }

            System.out.print("Skill level (1-10): ");
            int skill;
            try {
                skill = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid skill level.");
                return;
            }
            if (!ValidationUtil.validateSkillLevel(skill)) {
                System.out.println("Skill level out of range.");
                return;
            }

            System.out.print("Preferred role (STRATEGIST/ATTACKER/DEFENDER/SUPPORTER): ");
            String roleInput = scanner.nextLine().trim();
            Role role;
            try {
                role = Role.fromString(roleInput);
            } catch (Exception e) {
                System.out.println("Invalid role.");
                return;
            }

            // Run questionnaire and compute scaled score
            int scaledScore = questionnaire.runSurveyAndGetScaledScore(scanner);
            PersonalityType pType = PersonalityType.fromScore(scaledScore);

            // Create and add participant
            String id = UUID.randomUUID().toString();
            Participant p = new Participant(id, name, email, game, skill, role, scaledScore);

            if (participants == null) participants = new ArrayList<>();
            participants.add(p);

            System.out.println("\nNew participant created:");
            System.out.println("  ID: " + p.getId());
            System.out.println("  Name: " + p.getName());
            System.out.println("  Email: " + p.getEmail());
            System.out.println("  Game: " + p.getPreferredGame());
            System.out.println("  Skill: " + p.getSkillLevel());
            System.out.println("  Role: " + p.getPreferredRole());
            System.out.println("  Personality score: " + p.getPersonalityScore());
            System.out.println("  Personality type: " + pType.getDisplayName());

            // Optionally persist here: append to CSV or to your data store.
            // e.g., CSVHandler.appendParticipantToCsv(p, "data/participants_sample.csv");

        } catch (Exception e) {
            System.err.println("Failed to add participant: " + e.getMessage());
            LOGGER.warning("Add participant error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ConsoleUI ui = new ConsoleUI();
        ui.start();
    }
}

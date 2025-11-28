package controller;

import model.Participant;
import model.PersonalityType;
import model.Team;
import repository.ParticipantRepository;
import service.TeamFormationStrategy;
import service.Questionnaire;
import util.CSVHandler;
import java.io.*;
import java.util.*;


public class ParticipantController {
    private final Scanner scanner;
    private final Questionnaire questionnaire = new Questionnaire();
    private final ParticipantRepository repository;
    private final TeamFormationStrategy strategy;
    private final Participant loggedParticipant;

    private List<Participant> participants = new ArrayList<>();
    private List<Team> formedTeams = new ArrayList<>();

    public ParticipantController(Scanner scanner, ParticipantRepository repository,
                                 TeamFormationStrategy strategy, Participant loggedParticipant) {
        this.scanner = scanner;
        this.repository = repository;
        this.strategy = strategy;
        this.loggedParticipant = loggedParticipant;

        // Automatically load teams when participant logs in
        System.out.println("\n            Checking for existing teams...");
        loadFormedTeams();
    }

    /**
     * Load formed teams from CSV file automatically
     */
    private void loadFormedTeams() {
        String teamsFilePath = "data/formed_teams.csv";

        // Debug: Check if file exists
        File teamsFile = new File(teamsFilePath);
        System.out.println("            Debug: Teams file path: " + teamsFile.getAbsolutePath());
        System.out.println("            Debug: File exists? " + teamsFile.exists());

        if (!teamsFile.exists()) {
            System.out.println("            No teams file found. Teams have not been created yet.");
            formedTeams = new ArrayList<>();
            return;
        }

        try {
            formedTeams = CSVHandler.loadTeamsFromCSV(teamsFilePath);

            // Debug output
            System.out.println("            Debug: Loaded " + formedTeams.size() + " teams from file.");

            if (!formedTeams.isEmpty()) {
                System.out.println("            ✓ Found " + formedTeams.size() + " teams in the system.");

                // Debug: Show team IDs
                System.out.print("            Debug: Team IDs: ");
                for (Team t : formedTeams) {
                    System.out.print(t.getTeamId() + " ");
                }
                System.out.println();

                // Check if participant is in any team
                boolean found = false;
                for (Team t : formedTeams) {
                    for (Participant p : t.getMembers()) {
                        if (p.getId().equals(loggedParticipant.getId())) {
                            System.out.println("            ✓ You are assigned to team: " + t.getTeamName());
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }

                if (!found) {
                    System.out.println("            Note: You are not yet assigned to any team.");
                }
            } else {
                System.out.println("            No teams found in the file.");
            }

        } catch (IOException e) {
            System.err.println("            Error reading teams file: " + e.getMessage());
            e.printStackTrace();
            formedTeams = new ArrayList<>();
        } catch (Exception e) {
            System.err.println("            Unexpected error loading teams: " + e.getMessage());
            e.printStackTrace();
            formedTeams = new ArrayList<>();
        }
    }

    /**
     * Reload teams from CSV (in case admin updated them)
     */
    public void refreshTeams() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("            REFRESHING TEAMS FROM SYSTEM");
        System.out.println("=".repeat(55));
        loadFormedTeams();
    }

    public void displayParticipantStats() {
        System.out.println("\n" + "=".repeat(55));
        if (participants.isEmpty()) {
            System.out.println("            No participants loaded.");
            return;
        }

        System.out.println("            PARTICIPANT STATISTICS");
        System.out.println("            " + "-".repeat(40));

        for (PersonalityType t : PersonalityType.values()) {
            long c = participants.stream()
                    .filter(p -> p.getPersonalityType() == t)
                    .count();
            System.out.printf("            %-12s: %3d participants%n", t, c);
        }

        double avgSkill = participants.stream()
                .mapToInt(Participant::getSkillLevel)
                .average()
                .orElse(0.0);

        System.out.printf("%n            Average Skill Level: %.2f%n", avgSkill);
        System.out.printf("            Total Participants: %d%n", participants.size());
    }

    public void displayTeams() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("            ALL FORMED TEAMS");
        System.out.println("=".repeat(55));

        // Debug output
        System.out.println("            Debug: formedTeams list size = " + formedTeams.size());

        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams have been formed yet.");
            System.out.println("            Please contact an organizer to create teams.");
            System.out.println("            Or try option 4 to refresh teams from the system.");
            return;
        }

        for (Team t : formedTeams) {
            System.out.println();
            System.out.printf("            %s (%s)%n", t.getTeamName(), t.getTeamId());
            System.out.println("            " + "-".repeat(45));
            System.out.printf("            Members: %d | Avg Skill: %.2f | Roles: %d%n",
                    t.getCurrentSize(),
                    t.calculateAverageSkill(),
                    t.getUniqueRoleCount());
            System.out.println();

            for (Participant p : t.getMembers()) {
                String highlight = p.getId().equals(loggedParticipant.getId()) ? " ← YOU" : "";
                System.out.printf("              • %-20s | Skill: %2d | %-12s | %s%s%n",
                        p.getName(),
                        p.getSkillLevel(),
                        p.getPreferredRole(),
                        p.getPersonalityType(),
                        highlight);
            }
        }
        System.out.println("\n" + "=".repeat(55));
    }

    public void viewMyProfile() {
        if (loggedParticipant == null) {
            System.out.println("            No participant logged in.");
            return;
        }

        System.out.println("\n" + "=".repeat(55));
        System.out.println("            MY PROFILE");
        System.out.println("=".repeat(55));
        System.out.printf("            Name:        %s%n", loggedParticipant.getName());
        System.out.printf("            ID:          %s%n", loggedParticipant.getId());
        System.out.printf("            Email:       %s%n", loggedParticipant.getEmail());
        System.out.printf("            Game:        %s%n", loggedParticipant.getPreferredGame());
        System.out.printf("            Skill:       %d/10%n", loggedParticipant.getSkillLevel());
        System.out.printf("            Role:        %s%n", loggedParticipant.getPreferredRole());
        System.out.printf("            Personality: %s (Score: %d)%n",
                loggedParticipant.getPersonalityType() == null ?
                        "Unknown" : loggedParticipant.getPersonalityType().getDisplayName(),
                loggedParticipant.getPersonalityScore());
        System.out.println("=".repeat(55));
    }

    public void viewMyTeam() {
        if (loggedParticipant == null) {
            System.out.println("            No participant logged in.");
            return;
        }

        System.out.println("\n" + "=".repeat(55));
        System.out.println("            MY TEAM");
        System.out.println("=".repeat(55));

        // Debug output
        System.out.println("            Debug: Logged participant ID: " + loggedParticipant.getId());
        System.out.println("            Debug: formedTeams list size = " + formedTeams.size());

        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams have been formed yet.");
            System.out.println("            Please wait for an organizer to create teams.");
            System.out.println("            Or use option '4 - Refresh Teams' to check for updates.");
            return;
        }

        // Find the team containing this participant
        Team myTeam = null;
        for (Team t : formedTeams) {
            System.out.println("            Debug: Checking team " + t.getTeamId() + " with " + t.getCurrentSize() + " members");

            for (Participant p : t.getMembers()) {
                System.out.println("              Debug: Member ID: " + p.getId());

                if (p.getId().equals(loggedParticipant.getId())) {
                    myTeam = t;
                    System.out.println("              Debug: MATCH FOUND!");
                    break;
                }
            }
            if (myTeam != null) break;
        }

        if (myTeam == null) {
            System.out.println("            You are not currently assigned to any team.");
            System.out.println("            Please contact an organizer for team assignment.");
            return;
        }

        // Display team information
        System.out.println();
        System.out.printf("            Team Name:    %s%n", myTeam.getTeamName());
        System.out.printf("            Team ID:      %s%n", myTeam.getTeamId());
        System.out.printf("            Team Size:    %d members%n", myTeam.getCurrentSize());
        System.out.printf("            Avg Skill:    %.2f%n", myTeam.calculateAverageSkill());
        System.out.printf("            Unique Roles: %d%n", myTeam.getUniqueRoleCount());

        System.out.println();
        System.out.println("            TEAM MEMBERS:");
        System.out.println("            " + "-".repeat(45));

        for (Participant p : myTeam.getMembers()) {
            String highlight = p.getId().equals(loggedParticipant.getId()) ? " ← YOU" : "";
            System.out.printf("              • %-20s | Skill: %2d%n",
                    p.getName() + highlight, p.getSkillLevel());
            System.out.printf("                Role: %-12s | Type: %s%n",
                    p.getPreferredRole(), p.getPersonalityType());
            System.out.printf("                Game: %s%n", p.getPreferredGame());
            System.out.println();
        }

        System.out.println("=".repeat(55));
    }

    private String promptInput(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }
}
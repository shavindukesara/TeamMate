package main;

import model.*;
import service.*;
import util.*;

import java.util.*;
import java.util.logging.*;

public class TeamMateApplication {
    public class TeammateApplication {
        private static final Logger LOGGER = Logger.getLogger(TeammateApplication.class.getName());
        private static final Scanner scanner = new Scanner(System.in);

        public static void main(String[] args) {
            System.out.println("==============================================");
            System.out.println("  TeamMate: Team Formation System");
            System.out.println("  University Gaming Club");
            System.out.println("==============================================\n");

            try {
                List<Participant> participants = loadParticipantData();

                if (participants.isEmpty()) {
                    System.out.println("No participants loaded. Exiting.");
                    return;
                }

                int teamSize = getTeamSize(participants.size());

                System.out.println("\nForming teams...");
                List<Team> teams = MatchingAlgorithm.matchParticipants(participants, teamSize);

                displayTeams(teams);

                saveTeamsToFile(teams);

                System.out.println("\nTeam formation completed successfully!");

            } catch (Exception e) {
                LOGGER.severe("Application error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private static List<Participant> loadParticipantData() {
            System.out.print("Enter CSV file path (or press Enter for default): ");
            String filePath = scanner.nextLine().trim();

            if (filePath.isEmpty()) {
                filePath = "data/participants_sample.csv";
            }

            try {
                List<Participant> participants = CSVHandler.loadParticipants(filePath);
                return participants;
            } catch (Exception e) {
                LOGGER.severe("Failed to load participants: " + e.getMessage());
                return new ArrayList<>();
            }
        }
        private static int getTeamSize(int totalParticipants) {
            System.out.print("\nEnter desired team size (3-10): ");
            int teamSize = 5;

            try {
                teamSize = Integer.parseInt(scanner.nextLine().trim());
                if (!ValidationUtil.validateTeamSize(teamSize, totalParticipants)) {
                    System.out.println("Invalid team size, using default: 5");
                    teamSize = 5;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input, using default: 5");
            }
            return teamSize;
        }
        private static void displayTeams(List<Team> teams) {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("FORMED TEAMS");
            System.out.println("=".repeat(80));

            for (Team team : teams) {
                System.out.println("\n" + team.getTeamName() + " (" + team.getTeamId() + ")");
                System.out.println("-".repeat(80));
                System.out.printf("Average Skill: %.2f | Roles: %d | Members: %d\n",
                        team.calculateAverageSkill(),
                        team.getUniqueRoleCount(),
                        team.getCurrentSize());
                System.out.println();

                for (Participant p : team.getMembers()) {
                    System.out.printf("  %-15s | %-12s | Skill: %2d | %-11s | %-8s\n",
                            p.getName(),
                            p.getPreferredGame(),
                            p.getSkillLevel(),
                            p.getPreferredRole(),
                            p.getPersonalityType());
                }
            }
        }
        private static void saveTeamsToFile(List<Team> teams) {
            System.out.print("\nSave teams to file? (y/n): ");
            String response = scanner.nextLine().trim().toLowerCase();

            if (response.equals("y")) {
                try {
                    String outputPath = "data/formed_teams.csv";
                    CSVHandler.saveTeams(teams, outputPath);
                    System.out.println("Teams saved to: " + outputPath);
                } catch (Exception e) {
                    LOGGER.severe("Failed to save teams: " + e.getMessage());
                }
            }
        }

    }
}

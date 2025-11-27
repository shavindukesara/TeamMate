package controller;

import model.Participant;
import model.PersonalityType;
import model.Role;
import model.Team;
import repository.ParticipantRepository;
import service.TeamFormationStrategy;
import service.Questionnaire;
import util.ValidationUtil;
import util.ConcurrentCSVHandler;
import exception.TeamFormationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ParticipantController extends BaseController {
    private final Questionnaire questionnaire = new Questionnaire();
    private final ParticipantRepository repository;
    private final TeamFormationStrategy strategy;
    private List<Participant> participants = new ArrayList<>();
    private List<Team> formedTeams = new ArrayList<>();

    public ParticipantController(Scanner scanner, ParticipantRepository repository, TeamFormationStrategy strategy) {
        super(scanner);
        this.repository = repository;
        this.strategy = strategy;
    }

    public void loadParticipants(boolean concurrent) {
        System.out.println("\n" + "=".repeat(55));
        String file = prompt("            Please provide your CSV path : ");
        if (file.isEmpty()) file = "data/participants_sample.csv";
        try {
            if (concurrent) {
                participants = ConcurrentCSVHandler.loadParticipantsConcurrently(file);
            } else {
                participants = repository.load(file);
            }
            System.out.println("\n            Loaded " + participants.size() + " participants.");
        } catch (Exception e) {
            System.err.println("            Load failed: " + e.getMessage());
        }
    }

    public void displayParticipantStats() {
        System.out.println("\n" + "=".repeat(55));
        if (participants.isEmpty()) { System.out.println("            No participants found."); return; }
        for (PersonalityType t : PersonalityType.values()) {
            long c = participants.stream().filter(p -> p.getPersonalityType() == t).count();
            System.out.printf("            %s: %d%n", t, c);
        }
    }

    public void formTeams() {
        if (participants.isEmpty()) { System.out.println("            No participants found."); return; }
        int size;
        try {
            size = Integer.parseInt(prompt("            Team size: "));
        } catch (NumberFormatException e) { System.out.println("            Invalid."); return; }

        boolean randomMode = true;
        while (true) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("            Randomness Mode:");
            System.out.println("            1 - Random balanced teams");
            System.out.println("            2 - Deterministic balanced teams");
            String rm = prompt("\n            Your Choice: ");
            if ("1".equals(rm)) { randomMode = true; break; }
            if ("2".equals(rm)) { randomMode = false; break; }
            System.out.println("            Invalid choice. Please enter 1 or 2.");
        }

        try {
            formedTeams = new ArrayList<>(strategy.formTeams(participants, size, randomMode));
            System.out.println("            Formed " + formedTeams.size() + " teams.");
        } catch (TeamFormationException e) {
            System.err.println("            Formation failed: " + e.getMessage());
        }
    }

    public void displayTeams() {
        System.out.println("\n" + "=".repeat(55));
        if (formedTeams.isEmpty()) { System.out.println("            No teams found."); return; }
        for (Team t : formedTeams) {
            System.out.println("            " + t.getTeamName() + " (" + t.getTeamId() + ") members: " + t.getCurrentSize());
        }
    }

    public void exportTeams() {
        if (formedTeams.isEmpty()) { System.out.println("            No teams found."); return; }
        String file = prompt("\n            Output file (default: data/formed_teams.csv): ");
        if (file.isEmpty()) file = "data/formed_teams.csv";
        try {
            util.CSVHandler.saveTeams(formedTeams, file);
            System.out.println("\n            Exported to " + file);
        } catch (IOException e) {
            System.err.println("\n            Export failed: " + e.getMessage());
        }
    }

    public void analyzeTeams() {
        if (formedTeams.isEmpty()) { System.out.println("            No teams found."); return; }
        double globalAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        System.out.println("\n" + "=".repeat(55));
        System.out.printf("            Global Avg Skill: %.2f%n", globalAvg);
        for (Team t : formedTeams) {
            System.out.printf("            %s avg: %.2f%n", t.getTeamId(), t.calculateAverageSkill());
        }
    }

    public void addNewParticipant() {
        try {
            System.out.println("\n" + "=".repeat(55));
            String name = prompt("            Full name: ");
            String email = prompt("            Email: ");
            String game = prompt("            Preferred game (CS:GO, Basketball, Valorant, Chess, DOTA 2, FIFA): ");
            int skill = Integer.parseInt(prompt("            Skill (1-10) 1-Beginner 10-Professional: "));
            Role role = Role.fromString(prompt("            Preferred role (Strategist, Attacker, Defender, Supporter, Coordinator ): "));
            int scaled = questionnaire.runSurveyAndGetScaledScore(scanner);
            Participant p = new Participant(java.util.UUID.randomUUID().toString(), name, email, game, skill, role, scaled);
            if (participants == null) participants = new ArrayList<>();
            participants.add(p);
            boolean ok = repository.append(p, "data/participants_sample.csv");
            if (ok) System.out.println("            Participant saved.");
            else System.out.println("            Duplicate detected; not saved.");
            System.out.println("            Type: " + PersonalityType.fromScore(scaled).getDisplayName());
        } catch (Exception e) {
            System.err.println("            Add failed: " + e.getMessage());
        }
    }
}

package controller;

import model.Participant;
import model.PersonalityType;
import model.Role;
import model.Team;
import repository.ParticipantRepository;
import service.TeamFormationStrategy;
import service.Questionnaire;
import util.CSVHandler;
import util.ConcurrentCSVHandler;
import exception.TeamFormationException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ParticipantController extends BaseController {
    private final Questionnaire questionnaire = new Questionnaire();
    private final ParticipantRepository repository;
    private final TeamFormationStrategy strategy;
    private List<Participant> participants = new ArrayList<>();
    private List<Team> formedTeams = new ArrayList<>();

    public ParticipantController(java.util.Scanner scanner, ParticipantRepository repository, TeamFormationStrategy strategy) {
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

        boolean randomMode;
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
            System.out.println("Formed " + formedTeams.size() + " teams.");
            List<Team> unstable = formedTeams.stream()
                    .filter(t -> service.MatchingAlgorithm.teamViolatesPublic(t))
                    .collect(Collectors.toList());
            if (!unstable.isEmpty()) {
                System.out.println("\n" + "=".repeat(55));
                System.out.println("            ⚠ Unstable Teams Detected ⚠");
                System.out.println("            " + unstable.stream()
                        .map(Team::getTeamId)
                        .collect(Collectors.joining(", ")));
                System.out.println("            → Their members were written to data/leftovers.csv");
            } else {
                System.out.println("            All teams satisfy personality & role rules.");
            }
            System.out.println("If you don't like the balance you can reshuffle to try improving it.");
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
            CSVHandler.saveTeams(formedTeams, file);
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
            System.out.println("            Type: " + p.getPersonalityType().getDisplayName());
        } catch (Exception e) {
            System.err.println("            Add failed: " + e.getMessage());
        }
    }

    public void reshuffleAndAutoFix() {
        if (participants == null || participants.isEmpty()) {
            System.out.println("            No participants loaded. Please load participants first.");
            return;
        }
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No existing teams. Please form teams first.");
            return;
        }

        int teamSize = formedTeams.get(0).getMaxSize();

        System.out.println("\n" + "=".repeat(55));
        System.out.println("            Reshuffle & Auto-Fix");
        System.out.println("            1 - Quick reshuffle (fixed attempts)");
        System.out.println("            2 - Super reshuffle (batches until spread OK or cap reached)");
        System.out.print("            Your choice: ");
        String mode = prompt("");

        boolean randomMode = true;
        while (true) {
            String rm = prompt("            Use random mode? (y/n, default y): ");
            if (rm.isEmpty() || rm.equalsIgnoreCase("y")) { randomMode = true; break; }
            if (rm.equalsIgnoreCase("n")) { randomMode = false; break; }
            System.out.println("            Invalid input. Enter y or n.");
        }

        if ("1".equals(mode)) {
            int attempts;
            try {
                String s = prompt("            Attempts (recommended 10-20, default 15): ");
                attempts = s.isEmpty() ? 15 : Integer.parseInt(s);
            } catch (NumberFormatException e) {
                attempts = 15;
            }
            runQuickReshuffleAndAutoFix(attempts, randomMode, teamSize);
        } else if ("2".equals(mode)) {
            int maxTotal;
            try {
                String s = prompt("            Max total attempts (cap, default 200): ");
                maxTotal = s.isEmpty() ? 200 : Integer.parseInt(s);
            } catch (NumberFormatException e) {
                maxTotal = 200;
            }
            int batchSize;
            try {
                String s = prompt("            Batch size per cycle (default 15): ");
                batchSize = s.isEmpty() ? 15 : Integer.parseInt(s);
            } catch (NumberFormatException e) {
                batchSize = 15;
            }
            runSuperReshuffleAndAutoFix(maxTotal, batchSize, randomMode, teamSize);
        } else {
            System.out.println("            Invalid selection. Returning to menu.");
        }
    }

    private void runQuickReshuffleAndAutoFix(int attempts, boolean randomMode, int teamSize) {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("            Quick Reshuffle: running " + attempts + " attempt(s)...");
        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);

        for (int i = 1; i <= attempts; i++) {
            System.out.printf("            Attempt %d/%d ...%n", i, attempts);
            long start = System.currentTimeMillis();
            try {
                List<Team> candidate = strategy.formTeams(participants, teamSize, randomMode);
                double score = scoreTeams(candidate);
                long took = System.currentTimeMillis() - start;
                if (score < bestScore) {
                    bestScore = score;
                    best = new ArrayList<>(candidate);
                    System.out.printf("              → Improvement found (score %.3f). Time: %dms%n", score, took);
                } else {
                    System.out.printf("              no improvement (score %.3f). Time: %dms%n", score, took);
                }
            } catch (TeamFormationException e) {
                System.err.println("            Attempt " + i + " failed: " + e.getMessage());
            }
        }

        if (best == null || best.isEmpty()) {
            System.out.println("            Reshuffle did not produce any valid team sets.");
            return;
        }

        formedTeams = new ArrayList<>(best);
        System.out.println("\n            Reshuffle finished. Now running automatic surgical swaps to reduce spread...");
        boolean fixed = autoSurgicalFix(10); // up to 10 swaps
        try {
            CSVHandler.saveTeams(formedTeams, "data/formed_teams.csv");
            System.out.println("            Teams written to data/formed_teams.csv");
            System.out.println("            Leftovers at data/leftovers.csv");
        } catch (IOException e) {
            System.err.println("            Failed to save formed teams: " + e.getMessage());
        }
        System.out.println("            Auto-fix " + (fixed ? "succeeded" : "completed (no more improvements)"));
    }

    private void runSuperReshuffleAndAutoFix(int maxTotalAttempts, int batchSize, boolean randomMode, int teamSize) {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("            Super Reshuffle: up to " + maxTotalAttempts + " attempts in batches of " + batchSize);
        int attemptsDone = 0;
        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);
        double spreadThresholdFrac = service.MatchingAlgorithm.getSpreadThresholdFrac();

        while (attemptsDone < maxTotalAttempts) {
            int run = Math.min(batchSize, maxTotalAttempts - attemptsDone);
            System.out.printf("            Running batch: attempts %d - %d%n", attemptsDone + 1, attemptsDone + run);
            for (int i = 0; i < run; i++) {
                attemptsDone++;
                System.out.printf("              attempt %d/%d ...%n", attemptsDone, maxTotalAttempts);
                try {
                    List<Team> candidate = strategy.formTeams(participants, teamSize, randomMode);
                    double score = scoreTeams(candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new ArrayList<>(candidate);
                        System.out.printf("                → New best (score %.3f)%n", score);
                    }
                } catch (TeamFormationException e) {
                    System.err.println("                attempt failed: " + e.getMessage());
                }
            }

            double globalAvg = best.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
            double maxAvg = best.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(globalAvg);
            double minAvg = best.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(globalAvg);
            double spread = maxAvg - minAvg;

            System.out.printf("            After %d attempts: best score %.3f | spread %.3f (threshold %.3f)%n",
                    attemptsDone, bestScore, spread, globalAvg * spreadThresholdFrac);

            if (globalAvg > 0 && spread <= globalAvg * spreadThresholdFrac) {
                System.out.println("            Spread within acceptable threshold — stopping super-reshuffle.");
                break;
            } else {
                System.out.println("            Spread still high — continuing or will stop if cap reached.");
            }
        }

        if (best == null || best.isEmpty()) {
            System.out.println("            Super-reshuffle did not produce any valid team sets.");
            return;
        }

        formedTeams = new ArrayList<>(best);
        System.out.println("\n            Reshuffle finished. Now running automatic surgical swaps to reduce spread...");
        boolean fixed = autoSurgicalFix(25);
        try {
            CSVHandler.saveTeams(formedTeams, "data/formed_teams.csv");
            System.out.println("            Teams written to data/formed_teams.csv");
        } catch (IOException e) {
            System.err.println("            Failed to save formed teams: " + e.getMessage());
        }
        System.out.println("            Auto-fix " + (fixed ? "succeeded" : "completed (no more improvements)"));
    }

    private boolean autoSurgicalFix(int maxSwaps) {
        if (formedTeams == null || formedTeams.isEmpty()) return false;
        double spreadThreshFrac = service.MatchingAlgorithm.getSpreadThresholdFrac();
        boolean anyImproved = false;

        for (int attempt = 0; attempt < maxSwaps; attempt++) {
            double globalAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
            double maxAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(globalAvg);
            double minAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(globalAvg);
            double spread = maxAvg - minAvg;

            System.out.printf("            Surgical pass %d/%d — spread %.3f (threshold %.3f)%n",
                    attempt + 1, maxSwaps, spread, globalAvg * spreadThreshFrac);

            if (globalAvg > 0 && spread <= globalAvg * spreadThreshFrac) {
                return true;
            }

            Team weakest = formedTeams.stream()
                    .min(Comparator.comparingDouble(Team::calculateAverageSkill))
                    .orElse(null);
            if (weakest == null) break;

            Participant toReplace = weakest.getMembers().stream()
                    .min(Comparator.comparingInt(Participant::getSkillLevel))
                    .orElse(null);
            if (toReplace == null) break;

            List<Team> donors = formedTeams.stream()
                    .filter(t -> !t.getTeamId().equals(weakest.getTeamId()))
                    .filter(t -> t.calculateAverageSkill() > globalAvg)
                    .filter(t -> t.getMembers().stream().anyMatch(m -> m.getSkillLevel() >= 7))
                    .sorted(Comparator.comparingDouble(Team::calculateAverageSkill).reversed())
                    .collect(Collectors.toList());

            boolean swapped = false;
            for (Team donor : donors) {
                List<Participant> donorCandidates = donor.getMembers().stream()
                        .filter(m -> m.getSkillLevel() >= 7)
                        .sorted(Comparator.comparingInt(Participant::getSkillLevel).reversed())
                        .collect(Collectors.toList());

                for (Participant donorCandidate : donorCandidates) {
                    if (attemptSwap(donor, donorCandidate, weakest, toReplace)) {
                        swapped = true;
                        anyImproved = true;
                        break;
                    }
                }
                if (swapped) break;
            }

            if (!swapped) {
                System.out.println("            No valid swap found on this pass.");
                break;
            }
        }

        try {
            CSVHandler.saveTeams(formedTeams, "data/formed_teams.csv");
            System.out.println("            Teams persisted to data/formed_teams.csv");
        } catch (IOException e) {
            System.err.println("            Failed to persist teams: " + e.getMessage());
        }

        return anyImproved;
    }

    private boolean attemptSwap(Team donor, Participant pFromDonor, Team weakTeam, Participant pFromWeak) {
        if (donor == null || weakTeam == null || pFromDonor == null || pFromWeak == null) return false;

        List<Participant> donorMembers = new ArrayList<>(donor.getMembers());
        List<Participant> weakMembers = new ArrayList<>(weakTeam.getMembers());

        if (!donorMembers.removeIf(p -> p.getId().equals(pFromDonor.getId()))) return false;
        if (!weakMembers.removeIf(p -> p.getId().equals(pFromWeak.getId()))) return false;

        donorMembers.add(pFromWeak);
        weakMembers.add(pFromDonor);

        Team donorTemp = new Team(donor.getTeamId(), donor.getTeamName(), donor.getMaxSize());
        Team weakTemp = new Team(weakTeam.getTeamId(), weakTeam.getTeamName(), weakTeam.getMaxSize());
        for (Participant p : donorMembers) donorTemp.addMember(p);
        for (Participant p : weakMembers) weakTemp.addMember(p);

        boolean donorViolates = service.MatchingAlgorithm.teamViolatesPublic(donorTemp);
        boolean weakViolates = service.MatchingAlgorithm.teamViolatesPublic(weakTemp);
        if (donorViolates || weakViolates) return false;
        if (donorTemp.getUniqueRoleCount() < 3 || weakTemp.getUniqueRoleCount() < 3) return false;

        double maxBefore = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(0.0);
        double minBefore = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(0.0);
        double spreadBefore = maxBefore - minBefore;

        List<Team> copyTeamsAfter = new ArrayList<>();
        for (Team t : formedTeams) {
            if (t.getTeamId().equals(donor.getTeamId())) {
                Team nt = new Team(donor.getTeamId(), donor.getTeamName(), donor.getMaxSize());
                donorMembers.forEach(nt::addMember);
                copyTeamsAfter.add(nt);
            } else if (t.getTeamId().equals(weakTeam.getTeamId())) {
                Team nt = new Team(weakTeam.getTeamId(), weakTeam.getTeamName(), weakTeam.getMaxSize());
                weakMembers.forEach(nt::addMember);
                copyTeamsAfter.add(nt);
            } else {
                copyTeamsAfter.add(t);
            }
        }

        double maxAfter = copyTeamsAfter.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(0.0);
        double minAfter = copyTeamsAfter.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(0.0);
        double spreadAfter = maxAfter - minAfter;

        double weakBeforeDev = Math.abs(weakTeam.calculateAverageSkill() - formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0));
        double weakAfterDev = Math.abs(copyTeamsAfter.stream()
                .filter(t -> t.getTeamId().equals(weakTeam.getTeamId()))
                .findFirst().orElse(weakTeam).calculateAverageSkill()
                - copyTeamsAfter.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0));

        boolean improves = (spreadAfter < spreadBefore) || (spreadAfter == spreadBefore && weakAfterDev < weakBeforeDev);
        if (!improves) return false;

        boolean removedFromDonor = donor.removeMember(pFromDonor);
        boolean removedFromWeak = weakTeam.removeMember(pFromWeak);
        if (!removedFromDonor || !removedFromWeak) {
            if (removedFromDonor) donor.addMember(pFromDonor);
            if (removedFromWeak) weakTeam.addMember(pFromWeak);
            return false;
        }
        donor.addMember(pFromWeak);
        weakTeam.addMember(pFromDonor);

        System.out.printf("            Swapped: Donor %s -> %s (skill %d) <-> WeakTeam %s -> %s (skill %d)%n",
                donor.getTeamId(), pFromDonor.getName(), pFromDonor.getSkillLevel(),
                weakTeam.getTeamId(), pFromWeak.getName(), pFromWeak.getSkillLevel());
        return true;
    }

    private double scoreTeams(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return Double.MAX_VALUE;
        double globalAvg = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);

        double skillScore = 0.0;
        double personalityPenalty = 0.0;
        double rolePenalty = 0.0;

        for (Team t : teams) {
            double avg = t.calculateAverageSkill();
            skillScore += Math.abs(avg - globalAvg);

            if (!satisfiesPersonalityRule(t)) personalityPenalty += 10.0;

            int uniqueRoles = t.getUniqueRoleCount();
            if (uniqueRoles < 3) rolePenalty += (3 - uniqueRoles) * 2.0;
        }

        skillScore = skillScore / teams.size();

        return (skillScore * 1.0) + (personalityPenalty * 1.5) + (rolePenalty * 1.0);
    }

    private boolean satisfiesPersonalityRule(Team t) {
        int leaders = countByPersonality(t, PersonalityType.LEADER);
        int thinkers = countByPersonality(t, PersonalityType.THINKER);
        int size = t.getMaxSize();
        if (size == 1) {
            return leaders == 1 || thinkers == 1 || countByPersonality(t, PersonalityType.BALANCED) == 1;
        }
        if (size == 2) {
            return (leaders == 1 && (thinkers >= 1 || countByPersonality(t, PersonalityType.BALANCED) >= 1))
                    || (leaders == 0 && thinkers >= 1 && countByPersonality(t, PersonalityType.BALANCED) >= 1);
        }
        if (size == 3) {
            return leaders == 1 && (thinkers >= 1 || countByPersonality(t, PersonalityType.BALANCED) >= 2);
        }
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }

    private int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == type).count();
    }
}

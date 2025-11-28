package controller;

import model.Participant;
import model.PersonalityType;
import model.Team;
import repository.ParticipantRepository;
import service.TeamFormationStrategy;
import util.ConcurrentCSVHandler;
import util.CSVHandler;
import exception.TeamFormationException;
import exception.InvalidDataException;
import java.io.IOException;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class AdminController {
    private final Scanner scanner;
    private final ParticipantRepository repository;
    private final TeamFormationStrategy strategy;
    private List<Participant> participants = new ArrayList<>();
    private List<Team> formedTeams = new ArrayList<>();

    public AdminController(Scanner scanner, ParticipantRepository repository, TeamFormationStrategy strategy) {
        this.scanner = scanner;
        this.repository = repository;
        this.strategy = strategy;
    }

    public void loadParticipants(boolean concurrent) {
        System.out.println("\n" + "=".repeat(55));
        System.out.print("            Enter CSV path (press Enter for default): ");
        String file = scanner.nextLine().trim();
        if (file.isEmpty()) file = "data/participants_sample.csv";

        try {
            long startTime = System.currentTimeMillis();

            if (concurrent) {
                participants = ConcurrentCSVHandler.loadParticipantsConcurrently(file);
            } else {
                participants = repository.load(file);
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("            ✓ Loaded " + participants.size() +
                    " participants in " + duration + "ms.");

        } catch (IOException e) {
            System.err.println("            ✗ File error: " + e.getMessage());
            System.err.println("            Please check the file path and try again.");
            participants = new ArrayList<>();

        } catch (InvalidDataException e) {
            System.err.println("            ✗ Invalid data: " + e.getMessage());
            System.err.println("            Please verify CSV format matches the template.");
            participants = new ArrayList<>();

        } catch (InterruptedException e) {
            System.err.println("            ✗ Loading interrupted.");
            Thread.currentThread().interrupt();
            participants = new ArrayList<>();

        } catch (ExecutionException e) {
            System.err.println("            ✗ Concurrent processing failed: " + e.getMessage());
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("            Cause: " + cause.getMessage());
            }
            participants = new ArrayList<>();

        } catch (RuntimeException e) {
            System.err.println("            ✗ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            participants = new ArrayList<>();
        }
    }

    public void displayParticipantStats() {
        System.out.println("\n" + "=".repeat(55));
        if (participants == null || participants.isEmpty()) {
            System.out.println("            No participants loaded.");
            return;
        }
        for (PersonalityType t : PersonalityType.values()) {
            long c = participants.stream().filter(p -> p.getPersonalityType() == t).count();
            System.out.printf("            %s: %d%n", t, c);
        }
        double avg = participants.stream().mapToInt(Participant::getSkillLevel).average().orElse(0.0);
        System.out.printf("            Average skill (all participants): %.2f%n", avg);
    }

    private boolean chooseRandomMode() {
        while (true) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("            Randomness Mode:");
            System.out.println("            1 - Random balanced teams");
            System.out.println("            2 - Deterministic balanced teams");
            System.out.print("            Your Choice: ");
            String rm = scanner.nextLine().trim();
            if ("1".equals(rm)) return true;
            if ("2".equals(rm)) return false;
            System.out.println("            Invalid choice. Please enter 1 or 2.");
        }
    }

    public void formTeams() {
        if (participants == null || participants.isEmpty()) {
            System.out.println("            No participants loaded. Load participants first.");
            return;
        }

        int size;
        try {
            System.out.print("            Team size (>=1): ");
            size = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("            Invalid size.");
            return;
        }

        boolean randomMode = chooseRandomMode();

        try {
            formedTeams = new ArrayList<>(strategy.formTeams(participants, size, randomMode));
            System.out.println("            Formed " + formedTeams.size() + " teams.");

            List<Team> unstable = formedTeams.stream()
                    .filter(t -> service.MatchingAlgorithm.teamViolatesPublic(t))
                    .collect(Collectors.toList());
            if (!unstable.isEmpty()) {
                System.out.println("\n" + "=".repeat(55));
                System.out.println("            ⚠ Unstable Teams Detected ⚠");
                System.out.println("            " + unstable.stream().map(Team::getTeamId).collect(Collectors.joining(", ")));
                System.out.println("            Their members were written to data/leftovers.csv (see matching algorithm output).");
            } else {
                System.out.println("            All teams satisfy personality & role rules.");
            }
            try {
                CSVHandler.saveTeams(formedTeams, "data/formed_teams.csv");
                System.out.println("            Teams saved to data/formed_teams.csv");
            } catch (IOException ioe) {
                System.err.println("            Failed to write formed teams: " + ioe.getMessage());
            }
        } catch (TeamFormationException te) {
            System.err.println("            Formation failed: " + te.getMessage());
        } catch (Exception e) {
            System.err.println("            Unexpected error during formation: " + e.getMessage());
        }
    }

    public void displayTeams() {
        System.out.println("\n" + "=".repeat(55));
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams formed.");
            return;
        }
        for (Team t : formedTeams) {
            System.out.printf("            %s (%s) members: %d | avgSkill: %.2f%n",
                    t.getTeamName(), t.getTeamId(), t.getCurrentSize(), t.calculateAverageSkill());
            for (Participant p : t.getMembers()) {
                System.out.printf("                - %s | %s | skill:%d | %s | %s%n",
                        p.getName(), p.getId(), p.getSkillLevel(), p.getPreferredRole(), p.getPersonalityType());
            }
        }
    }

    public void viewTeamById(String teamId) {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams formed.");
            return;
        }

        Optional<Team> team = formedTeams.stream()
                .filter(t -> t.getTeamId().equalsIgnoreCase(teamId))
                .findFirst();

        if (team.isPresent()) {
            Team t = team.get();
            System.out.println("\n" + "=".repeat(55));
            System.out.printf("            Team: %s (%s)%n", t.getTeamName(), t.getTeamId());
            System.out.printf("            Size: %d/%d | Average Skill: %.2f%n",
                    t.getCurrentSize(), t.getMaxSize(), t.calculateAverageSkill());
            System.out.println("            Members:");
            for (Participant p : t.getMembers()) {
                System.out.printf("              - %s (%s)%n", p.getName(), p.getId());
                System.out.printf("                Skill: %d | Role: %s | Personality: %s%n",
                        p.getSkillLevel(), p.getPreferredRole(), p.getPersonalityType());
                System.out.printf("                Game: %s | Email: %s%n",
                        p.getPreferredGame(), p.getEmail());
            }
        } else {
            System.out.println("            Team not found: " + teamId);
        }
    }

    public void findTeamByParticipant(String participantId) {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams formed.");
            return;
        }

        for (Team team : formedTeams) {
            Optional<Participant> participant = team.getMembers().stream()
                    .filter(p -> p.getId().equalsIgnoreCase(participantId))
                    .findFirst();

            if (participant.isPresent()) {
                viewTeamById(team.getTeamId());
                return;
            }
        }
        System.out.println("            Participant not found in any team: " + participantId);
    }

    public void exportTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams to export.");
            return;
        }
        System.out.print("            Output file (default: data/formed_teams.csv): ");
        String file = scanner.nextLine().trim();
        if (file.isEmpty()) file = "data/formed_teams.csv";
        try {
            CSVHandler.saveTeams(formedTeams, file);
            System.out.println("            Exported to " + file);
        } catch (IOException e) {
            System.err.println("            Export failed: " + e.getMessage());
        }
    }

    public void analyzeTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No teams to analyze.");
            return;
        }
        double globalAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        System.out.printf("\n            Global Avg Skill: %.2f%n", globalAvg);

        System.out.println("            Team skill breakdown:");
        formedTeams.stream()
                .sorted(Comparator.comparingDouble(Team::calculateAverageSkill).reversed())
                .forEach(t -> System.out.printf("              %s: %.2f%n", t.getTeamId(), t.calculateAverageSkill()));

        System.out.println("\n            Role & Personality warnings:");
        formedTeams.forEach(t -> {
            if (t.getUniqueRoleCount() < 3) System.out.println("              WARNING: " + t.getTeamId() + " has fewer than 3 unique roles");
            int leaders = (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.LEADER).count();
            int thinkers = (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.THINKER).count();
            if (!(leaders == 1 && thinkers >= 1 && thinkers <= 2)) {
                System.out.println("              WARNING: " + t.getTeamId() + " violates personality rule (1 leader; 1-2 thinkers)");
            }
        });
    }

    public void rebalanceTeams(int attempts, boolean randomMode) {
        if (participants == null || participants.isEmpty()) {
            System.out.println("            No participants loaded. Load participants first.");
            return;
        }
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No existing teams. Form teams first.");
            return;
        }
        if (attempts <= 0) attempts = 15;

        System.out.println("\n" + "=".repeat(55));
        System.out.println("            Rebalance: running " + attempts + " attempts (" + (randomMode ? "random" : "deterministic") + ")...");

        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);
        int teamSize = formedTeams.get(0).getMaxSize();

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
            } catch (TeamFormationException te) {
                System.out.printf("              Attempt %d failed: %s%n", i, te.getMessage());
            } catch (Exception e) {
                System.out.printf("              Attempt %d unexpected error: %s%n", i, e.getMessage());
            }
        }

        if (best == null || best.isEmpty()) {
            System.out.println("            Rebalance did not produce any valid team sets.");
            return;
        }

        double oldScore = scoreTeams(formedTeams);
        formedTeams = new ArrayList<>(best);

        System.out.println("\n            Best candidate selected. Now performing automatic surgical swaps (auto-fix)...");
        boolean improved = autoSurgicalFixAdmin(25);

        double newScore = scoreTeams(formedTeams);
        System.out.println("\n            Rebalance completed.");
        System.out.printf("            Old score: %.3f | New best score: %.3f%n", oldScore, newScore);
        if (improved) System.out.println("            Auto-surgical swaps improved teams.");
        else System.out.println("            No further improvement from surgical swaps.");

        try {
            CSVHandler.saveTeams(formedTeams, "data/formed_teams.csv");
            System.out.println("            Teams persisted to data/formed_teams.csv");
        } catch (IOException e) {
            System.err.println("            Failed to persist teams: " + e.getMessage());
        }
    }

    public void superBalance(int maxTotalAttempts, int batchSize, boolean randomMode) {
        if (participants == null || participants.isEmpty()) {
            System.out.println("            No participants loaded. Load participants first.");
            return;
        }
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("            No existing teams. Form teams first.");
            return;
        }
        if (maxTotalAttempts <= 0) maxTotalAttempts = 200;
        if (batchSize <= 0) batchSize = 15;

        double spreadThresholdFrac = service.MatchingAlgorithm.getSpreadThresholdFrac();

        System.out.println("\n" + "=".repeat(55));
        System.out.println("            Super-balance: cap " + maxTotalAttempts + " attempts, batch size " + batchSize);

        int attemptsDone = 0;
        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);
        int teamSize = formedTeams.get(0).getMaxSize();

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
                } catch (Exception e) {
                    System.err.println("                attempt unexpected error: " + e.getMessage());
                }
            }

            double globalAvg = best.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
            double maxAvg = best.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(globalAvg);
            double minAvg = best.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(globalAvg);
            double spread = maxAvg - minAvg;

            System.out.printf("            After %d attempts: best score %.3f | spread %.3f (threshold %.3f)%n",
                    attemptsDone, bestScore, spread, globalAvg * spreadThresholdFrac);

            if (globalAvg > 0 && spread <= globalAvg * spreadThresholdFrac) {
                System.out.println("            Spread within acceptable threshold — stopping super-balance.");
                break;
            } else {
                System.out.println("            Spread still high — continuing or will stop if cap reached.");
            }
        }

        if (best == null || best.isEmpty()) {
            System.out.println("            Super-balance did not produce any valid team sets.");
            return;
        }

        formedTeams = new ArrayList<>(best);
        System.out.println("\n            Best candidate selected. Now running automatic surgical swaps to reduce spread...");
        boolean fixed = autoSurgicalFixAdmin(50);

        try {
            CSVHandler.saveTeams(formedTeams, "data/formed_teams.csv");
            System.out.println("            Teams written to data/formed_teams.csv");
        } catch (IOException e) {
            System.err.println("            Failed to save formed teams: " + e.getMessage());
        }

        System.out.println("            Auto-fix " + (fixed ? "succeeded" : "completed (no more improvements)"));
    }

    private boolean autoSurgicalFixAdmin(int maxSwaps) {
        if (formedTeams == null || formedTeams.isEmpty()) return false;
        boolean anyImproved = false;
        for (int pass = 0; pass < maxSwaps; pass++) {
            boolean didSwap = false;

            double globalAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
            double maxAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(globalAvg);
            double minAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(globalAvg);
            double currentSpread = maxAvg - minAvg;

            System.out.printf("            Surgical pass %d/%d — spread %.3f (globalAvg %.3f)%n",
                    pass + 1, maxSwaps, currentSpread, globalAvg);

            Team weakest = formedTeams.stream().min(Comparator.comparingDouble(Team::calculateAverageSkill)).orElse(null);
            if (weakest == null) break;

            Participant lowest = weakest.getMembers().stream()
                    .min(Comparator.comparingInt(Participant::getSkillLevel))
                    .orElse(null);
            if (lowest == null) break;

            List<Team> donors = formedTeams.stream()
                    .filter(t -> !t.getTeamId().equals(weakest.getTeamId()))
                    .filter(t -> t.calculateAverageSkill() > globalAvg)
                    .filter(t -> t.getMembers().stream().anyMatch(m -> m.getSkillLevel() >= 7))
                    .sorted(Comparator.comparingDouble(Team::calculateAverageSkill).reversed())
                    .collect(Collectors.toList());

            for (Team donor : donors) {
                List<Participant> donorCandidates = donor.getMembers().stream()
                        .filter(m -> m.getSkillLevel() >= 7)
                        .sorted(Comparator.comparingInt(Participant::getSkillLevel).reversed())
                        .collect(Collectors.toList());

                for (Participant donorCandidate : donorCandidates) {
                    if (attemptAdminSwap(donor, donorCandidate, weakest, lowest)) {
                        didSwap = true;
                        anyImproved = true;
                        break;
                    }
                }
                if (didSwap) break;
            }

            if (!didSwap) {
                System.out.println("            No valid swap found on this pass.");
                break;
            }
        }
        return anyImproved;
    }

    private boolean attemptAdminSwap(Team donor, Participant donorCandidate, Team weakTeam, Participant weakCandidate) {
        if (donor == null || donorCandidate == null || weakTeam == null || weakCandidate == null) return false;

        List<Participant> donorAfter = donor.getMembers().stream()
                .filter(m -> !m.getId().equals(donorCandidate.getId()))
                .collect(Collectors.toList());
        donorAfter.add(weakCandidate);

        List<Participant> weakAfter = weakTeam.getMembers().stream()
                .filter(m -> !m.getId().equals(weakCandidate.getId()))
                .collect(Collectors.toList());
        weakAfter.add(donorCandidate);

        Team donorTemp = new Team(donor.getTeamId(), donor.getTeamName(), donor.getMaxSize());
        donorAfter.forEach(donorTemp::addMember);
        Team weakTemp = new Team(weakTeam.getTeamId(), weakTeam.getTeamName(), weakTeam.getMaxSize());
        weakAfter.forEach(weakTemp::addMember);

        if (service.MatchingAlgorithm.teamViolatesPublic(donorTemp)) return false;
        if (service.MatchingAlgorithm.teamViolatesPublic(weakTemp)) return false;
        if (donorTemp.getUniqueRoleCount() < 3 || weakTemp.getUniqueRoleCount() < 3) return false;

        double spreadBefore = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(0.0)
                - formedTeams.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(0.0);

        List<Team> afterList = new ArrayList<>();
        for (Team t : formedTeams) {
            if (t.getTeamId().equals(donor.getTeamId())) afterList.add(donorTemp);
            else if (t.getTeamId().equals(weakTeam.getTeamId())) afterList.add(weakTemp);
            else afterList.add(t);
        }

        double spreadAfter = afterList.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(0.0)
                - afterList.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(0.0);

        double globalAvgBefore = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        double weakBeforeDev = Math.abs(weakTeam.calculateAverageSkill() - globalAvgBefore);

        double globalAvgAfter = afterList.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        double weakAfterDev = Math.abs(
                afterList.stream().filter(t -> t.getTeamId().equals(weakTeam.getTeamId())).findFirst()
                        .orElse(weakTeam).calculateAverageSkill() - globalAvgAfter);

        boolean improves = (spreadAfter < spreadBefore) || (spreadAfter == spreadBefore && weakAfterDev < weakBeforeDev);
        if (!improves) return false;

        boolean removedFromDonor = donor.removeMember(donorCandidate);
        boolean removedFromWeak = weakTeam.removeMember(weakCandidate);
        if (!removedFromDonor || !removedFromWeak) {
            if (removedFromDonor) donor.addMember(donorCandidate);
            if (removedFromWeak) weakTeam.addMember(weakCandidate);
            return false;
        }

        donor.addMember(weakCandidate);
        weakTeam.addMember(donorCandidate);

        System.out.printf("            Swapped: %s (%s skill %d) <-> %s (%s skill %d)%n",
                donor.getTeamId(), donorCandidate.getName(), donorCandidate.getSkillLevel(),
                weakTeam.getTeamId(), weakCandidate.getName(), weakCandidate.getSkillLevel());

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
            if (!satisfiesPersonalityRuleForMembers(t.getMembers(), t.getMaxSize())) personalityPenalty += 10.0;
            int uniqueRoles = t.getUniqueRoleCount();
            if (uniqueRoles < 3) rolePenalty += (3 - uniqueRoles) * 2.0;
        }
        skillScore = skillScore / teams.size();
        return (skillScore * 1.0) + (personalityPenalty * 1.5) + (rolePenalty * 1.0);
    }

    private boolean satisfiesPersonalityRuleForMembers(List<Participant> members, int teamSize) {
        long leaders = members.stream().filter(m -> m.getPersonalityType() == PersonalityType.LEADER).count();
        long thinkers = members.stream().filter(m -> m.getPersonalityType() == PersonalityType.THINKER).count();
        long balanced = members.stream().filter(m -> m.getPersonalityType() == PersonalityType.BALANCED).count();

        if (teamSize == 1) return leaders == 1 || thinkers == 1 || balanced == 1;
        if (teamSize == 2) {
            if (leaders == 1 && (thinkers >= 1 || balanced >= 1)) return true;
            if (leaders == 0 && thinkers >= 1 && balanced >= 1) return true;
            return false;
        }
        if (teamSize == 3) return leaders == 1 && (thinkers >= 1 || balanced >= 2);
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }
}
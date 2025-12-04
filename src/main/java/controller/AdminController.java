package controller;

import model.Participant;
import model.PersonalityType;
import model.Team;
import repository.ParticipantRepository;
import service.TeamFormationStrategy;
import util.ConcurrentCSVHandler;
import util.CSVHandler;
import exception.TeamFormationException;
import java.io.IOException;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

public class AdminController extends BaseController {
    private final ParticipantRepository repository;
    private final TeamFormationStrategy strategy;
    private List<Participant> participants = new ArrayList<>();
    private List<Team> formedTeams = new ArrayList<>();

    private static final String DEFAULT_PARTICIPANTS_CSV = "data/participants_sample.csv";
    private static final String FORMED_TEAMS_CSV = "data/formed_teams.csv";

    public AdminController(Scanner scanner, ParticipantRepository repository, TeamFormationStrategy strategy) {
        super(scanner);
        this.repository = repository;
        this.strategy = strategy;
    }

    public void loadParticipants(boolean concurrent) {
        System.out.println("\n" + "=".repeat(55));
        System.out.print("Enter CSV path (press Enter for default): ");
        String file = scanner.nextLine().trim();
        if (file.isEmpty()) file = DEFAULT_PARTICIPANTS_CSV;
        try {
            if (concurrent) {
                participants = ConcurrentCSVHandler.loadParticipantsConcurrently(file);
            } else {
                participants = repository.load(file);
            }
            System.out.println("Loaded " + participants.size() + " participants.");
        } catch (IOException | RuntimeException e) {
            System.err.println("Load failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Load failed: " + e.getMessage());
        }
    }

    public void displayParticipantStats() {
        System.out.println("\n" + "=".repeat(55));
        if (participants == null || participants.isEmpty()) {
            System.out.println("No participants loaded.");
            return;
        }
        for (PersonalityType t : PersonalityType.values()) {
            long c = participants.stream().filter(p -> p.getPersonalityType() == t).count();
            System.out.printf("%s: %d%n", t, c);
        }
        double avg = participants.stream().mapToInt(Participant::getSkillLevel).average().orElse(0.0);
        System.out.printf("Average skill (all participants): %.2f%n", avg);
    }


    public void formTeams() throws InterruptedException {
        ensureParticipantsLoadedFromDiskIfNeeded();

        if (participants == null || participants.isEmpty()) {
            System.out.println("No participants loaded. Load participants first.");
            return;
        }

        int size;
        try {
            System.out.print("Team size: ");
            size = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid size.");
            return;
        }

        boolean randomMode = chooseRandomMode();

        try {
            formedTeams = new ArrayList<>(strategy.formTeams(participants, size, randomMode));
            System.out.println("            Formed " + formedTeams.size() + " teams.");
            try {
                CSVHandler.saveTeams(formedTeams, FORMED_TEAMS_CSV);
                System.out.println("            Teams saved to " + FORMED_TEAMS_CSV);
            } catch (IOException ioe) {
                System.err.println("            Failed to write formed teams: " + ioe.getMessage());
            }

            // show unstable teams
            List<Team> unstable = formedTeams.stream()
                    .filter(t -> service.MatchingAlgorithm.teamViolatesPublic(t))
                    .collect(Collectors.toList());
            if (!unstable.isEmpty()) {
                System.out.println("\n" + "=".repeat(55));
                System.out.println("Unstable Teams Detected");
                System.out.println(" " + unstable.stream().map(Team::getTeamId).collect(Collectors.joining(", ")));
                System.out.println("Their members were written to leftovers.csv.");
            } else {
                System.out.println("All teams satisfy personality & role rules.");
            }
        } catch (TeamFormationException te) {
            System.err.println("Formation failed: " + te.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during formation: " + e.getMessage());
        }
    }

    public void rebalanceTeams(int attempts, boolean randomMode) throws InterruptedException {
        if (participants == null || participants.isEmpty()) {
            System.out.println("No participants loaded. Please load participants first.");
            return;
        }
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("No existing teams. Please form teams first.");
            return;
        }
        if (attempts <= 0) attempts = 15;

        System.out.println("\n" + "=".repeat(55));
        System.out.println("Rebalance: running " + attempts + " attempts (" + (randomMode ? "random" : "deterministic") + ")...");

        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);
        int teamSize = formedTeams.get(0).getMaxSize();

        for (int i = 1; i <= attempts; i++) {
            System.out.printf("Attempt %d/%d ...%n", i, attempts);
            long start = System.currentTimeMillis();
            try {
                List<Team> candidate = strategy.formTeams(participants, teamSize, randomMode);
                double score = scoreTeams(candidate);
                long took = System.currentTimeMillis() - start;
                if (score < bestScore) {
                    bestScore = score;
                    best = new ArrayList<>(candidate);
                    System.out.printf("Improvement found (score %.3f). Time: %dms%n", score, took);
                } else {
                    System.out.printf("No improvement (score %.3f). Time: %dms%n", score, took);
                }
            } catch (TeamFormationException te) {
                System.out.printf("Attempt %d failed: %s%n", i, te.getMessage());
            } catch (Exception e) {
                System.out.printf("Attempt %d unexpected error: %s%n", i, e.getMessage());
            }
        }

        if (best == null || best.isEmpty()) {
            System.out.println("Rebalance did not produce any valid team sets.");
            return;
        }

        double oldScore = scoreTeams(formedTeams);
        formedTeams = new ArrayList<>(best);

        System.out.println("\nBest candidate selected. Now performing automatic surgical swaps...");
        boolean improved = autoSurgicalFixAdmin(25);

        double newScore = scoreTeams(formedTeams);
        System.out.println("\nRebalance completed.");
        System.out.printf("Old score: %.3f | New best score: %.3f%n", oldScore, newScore);

        if (improved) System.out.println("Auto-swaps improved teams.");

        try {
            CSVHandler.saveTeams(formedTeams, FORMED_TEAMS_CSV);
            System.out.println("Teams persisted to " + FORMED_TEAMS_CSV);
        } catch (IOException e) {
            System.err.println("Failed to persist teams: " + e.getMessage());
        }
    }

    public void superBalance(int maxTotalAttempts, int batchSize, boolean randomMode) throws InterruptedException {
        if (participants == null || participants.isEmpty()) {
            System.out.println("No participants loaded. Please load participants.");
            return;
        }
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("No existing teams. Please form teams.");
            return;
        }
        if (maxTotalAttempts <= 0) maxTotalAttempts = 200;
        if (batchSize <= 0) batchSize = 15;

        double spreadThresholdFrac = service.MatchingAlgorithm.getSpreadThresholdFrac();

        System.out.println("\n" + "=".repeat(55));
        System.out.println("Super-balance: cap " + maxTotalAttempts + " attempts, batch size " + batchSize);

        int attemptsDone = 0;
        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);
        int teamSize = formedTeams.get(0).getMaxSize();

        while (attemptsDone < maxTotalAttempts) {
            int run = Math.min(batchSize, maxTotalAttempts - attemptsDone);
            System.out.printf("Running batch: attempts %d - %d%n", attemptsDone + 1, attemptsDone + run);
            for (int i = 0; i < run; i++) {
                attemptsDone++;
                System.out.printf("Attempt %d/%d ...%n", attemptsDone, maxTotalAttempts);
                try {
                    List<Team> candidate = strategy.formTeams(participants, teamSize, randomMode);
                    double score = scoreTeams(candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new ArrayList<>(candidate);
                        System.out.printf("New best (score %.3f)%n", score);
                    }
                } catch (TeamFormationException e) {
                    System.err.println("attempt failed: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("attempt unexpected error: " + e.getMessage());
                }
            }

            double globalAvg = best.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
            double maxAvg = best.stream().mapToDouble(Team::calculateAverageSkill).max().orElse(globalAvg);
            double minAvg = best.stream().mapToDouble(Team::calculateAverageSkill).min().orElse(globalAvg);
            double spread = maxAvg - minAvg;

            System.out.printf("After %d attempts: best score %.3f | spread %.3f (threshold %.3f)%n",
                    attemptsDone, bestScore, spread, globalAvg * spreadThresholdFrac);

            if (globalAvg > 0 && spread <= globalAvg * spreadThresholdFrac) {
                System.out.println("Spread within acceptable threshold.");
                break;
            } else {
                System.out.println("Spread still high — continuing or will stop if cap reached.");
            }
        }

        if (best == null || best.isEmpty()) {
            System.out.println("Super-balance did not produce any valid team sets.");
            return;
        }

        formedTeams = new ArrayList<>(best);
        System.out.println("\nBest candidate selected. Now running automatic swaps to reduce spread...");
        boolean fixed = autoSurgicalFixAdmin(50);

        try {
            CSVHandler.saveTeams(formedTeams, FORMED_TEAMS_CSV);
            System.out.println("Teams written to " + FORMED_TEAMS_CSV);
        } catch (IOException e) {
            System.err.println("Failed to save formed teams: " + e.getMessage());
        }

        System.out.println("Auto-fix " + (fixed ? "succeeded" : "completed (no more improvements)"));
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

            System.out.printf("Surgical pass %d/%d — spread %.3f (globalAvg %.3f)%n",
                    pass + 1, maxSwaps, currentSpread, globalAvg);

            Team weakest = formedTeams.stream().min(Comparator.comparingDouble(Team::calculateAverageSkill)).orElse(null);
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

            for (Team donor : donors) {
                List<Participant> donorCandidates = donor.getMembers().stream()
                        .filter(m -> m.getSkillLevel() >= 7)
                        .sorted(Comparator.comparingInt(Participant::getSkillLevel).reversed())
                        .collect(Collectors.toList());

                for (Participant donorCandidate : donorCandidates) {
                    if (attemptAdminSwap(donor, donorCandidate, weakest, toReplace)) {
                        didSwap = true;
                        anyImproved = true;
                        break;
                    }
                }
                if (didSwap) break;
            }

            if (!didSwap) {
                System.out.println("No valid swap found on this pass.");
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

        System.out.printf("Swapped: %s (%s skill %d) <-> %s (%s skill %d)%n",
                donor.getTeamId(), donorCandidate.getName(), donorCandidate.getSkillLevel(),
                weakTeam.getTeamId(), weakCandidate.getName(), weakCandidate.getSkillLevel());

        return true;
    }


    private boolean chooseRandomMode() {
        while (true) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("Randomness Mode:");
            System.out.println("1 - Random balanced teams");
            System.out.println("2 - Deterministic balanced teams");
            System.out.print("Your Choice: ");
            String rm = scanner.nextLine().trim();
            if ("1".equals(rm)) return true;
            if ("2".equals(rm)) return false;
            System.out.println("Invalid choice. Please enter 1 or 2.");
        }
    }

    private void ensureParticipantsLoadedFromDiskIfNeeded() {

        try {
            List<Participant> fresh = repository.load(DEFAULT_PARTICIPANTS_CSV);
            if (fresh != null) participants = fresh;
            System.out.println("(Auto) reloaded participants: " + participants.size() + " participants.");
        } catch (Exception e) {
            // fallback to existing in-memory participants if load fails
            System.err.println("Warning: failed to reload participants: " + e.getMessage());
        }
    }

    public void displayTeams() {
        System.out.println("\n" + "=".repeat(55));
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("No teams formed.");
            return;
        }
        for (Team t : formedTeams) {
            System.out.printf("%s (%s) members: %d | avgSkill: %.2f%n",
                    t.getTeamName(), t.getTeamId(), t.getCurrentSize(), t.calculateAverageSkill());
            for (Participant p : t.getMembers()) {
                System.out.printf("- %s | %s | skill:%d | %s | %s%n",
                        p.getName(), p.getId(), p.getSkillLevel(), p.getPreferredRole(), p.getPersonalityType());
            }
        }
    }

    public void exportTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("No teams to export.");
            return;
        }
        System.out.print("Output file path: ");
        String file = scanner.nextLine().trim();
        if (file.isEmpty()) file = FORMED_TEAMS_CSV;
        try {
            CSVHandler.saveTeams(formedTeams, file);
            System.out.println("Exported to " + file);
        } catch (IOException e) {
            System.err.println("Export failed: " + e.getMessage());
        }
    }

    public void analyzeTeams() {
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("No teams to analyze.");
            return;
        }
        double globalAvg = formedTeams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        System.out.printf("\nGlobal Avg Skill: %.2f%n", globalAvg);

        System.out.println("Team skill breakdown:");
        formedTeams.stream()
                .sorted(Comparator.comparingDouble(Team::calculateAverageSkill).reversed())
                .forEach(t -> System.out.printf("%s: %.2f%n", t.getTeamId(), t.calculateAverageSkill()));

        System.out.println("\nRole & Personality warnings:");
        formedTeams.forEach(t -> {
            if (t.getUniqueRoleCount() < 3) System.out.println("WARNING: " + t.getTeamId() + " has fewer than 3 unique roles");
            int leaders = (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.LEADER).count();
            int thinkers = (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.THINKER).count();
            if (!(leaders == 1 && thinkers >= 1 && thinkers <= 2)) {
                System.out.println("WARNING: " + t.getTeamId() + " violates personality rules.");
            }
        });
    }


    public void reshuffleAndAutoFix(int attempts, boolean randomMode) throws InterruptedException {
        if (participants == null || participants.isEmpty()) {
            System.out.println("No participants loaded. Please load participants.");
            return;
        }
        if (formedTeams == null || formedTeams.isEmpty()) {
            System.out.println("No existing teams. Please form teams.");
            return;
        }
        if (attempts <= 0) attempts = 10;
        System.out.println("\n" + "=".repeat(55));
        System.out.println("Reshuffle: running " + attempts + " attempts...");

        double bestScore = scoreTeams(formedTeams);
        List<Team> best = new ArrayList<>(formedTeams);

        int teamSize = formedTeams.get(0).getMaxSize();
        for (int i = 1; i <= attempts; i++) {
            System.out.printf("Attempt %d/%d ...%n", i, attempts);
            try {
                List<Team> candidate = strategy.formTeams(participants, teamSize, randomMode);
                double score = scoreTeams(candidate);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    System.out.printf("Improvement found (score %.3f)%n", score);
                } else {
                    System.out.printf("No improvement found (score %.3f)%n", score);
                }
            } catch (TeamFormationException te) {
                System.out.printf("Attempt %d failed: %s%n", i, te.getMessage());
            } catch (Exception e) {
                System.out.printf("Attempt %d unexpected error: %s%n", i, e.getMessage());
            }
        }

        List<Team> improved = surgicalFixWeakestTeams(best);
        double improvedScore = scoreTeams(improved);
        if (improvedScore < bestScore) {
            bestScore = improvedScore;
            best = improved;
            System.out.println("Fix improved teams.");
        }

        formedTeams = new ArrayList<>(best);
        try {
            CSVHandler.saveTeams(formedTeams, FORMED_TEAMS_CSV);
        } catch (IOException e) {
            System.err.println("Failed to save formed teams: " + e.getMessage());
        }
        System.out.println("Reshuffle completed. Best score: " + bestScore);
    }

    private List<Team> surgicalFixWeakestTeams(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return teams;
        double globalAvg = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        Team weakest = teams.stream().min(Comparator.comparingDouble(Team::calculateAverageSkill)).orElse(null);
        if (weakest == null) return teams;

        List<Team> donors = teams.stream()
                .filter(t -> t != weakest && t.calculateAverageSkill() > globalAvg + 0.5)
                .sorted(Comparator.comparingDouble(Team::calculateAverageSkill).reversed())
                .collect(Collectors.toList());

        for (Team donor : donors) {
            for (Participant candidate : donor.getMembers()) {
                if (candidate.getSkillLevel() > weakest.calculateAverageSkill()) {
                    Participant low = weakest.getMembers().stream()
                            .min(Comparator.comparingInt(Participant::getSkillLevel)).orElse(null);
                    if (low == null) continue;
                    donor.removeMember(candidate);
                    weakest.addMember(candidate);
                    weakest.removeMember(low);
                    donor.addMember(low);
                    if (!matchesPersonalityAndRoleConstraints(weakest) || !matchesPersonalityAndRoleConstraints(donor)) {
                        donor.removeMember(low);
                        donor.addMember(candidate);
                        weakest.removeMember(candidate);
                        weakest.addMember(low);
                    } else {
                        return teams;
                    }
                }
            }
        }
        return teams;
    }

    private boolean matchesPersonalityAndRoleConstraints(Team t) {
        int leaders = (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.LEADER).count();
        int thinkers = (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.THINKER).count();
        boolean personalityOk = (leaders == 1 && thinkers >= 1 && thinkers <= 2) ||
                (t.getMaxSize() == 1 && (leaders == 1 || thinkers == 1));
        boolean rolesOk = t.getUniqueRoleCount() >= 3 || t.getCurrentSize() < 3;
        return personalityOk && rolesOk;
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

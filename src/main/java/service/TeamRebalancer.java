package service;

import model.Participant;
import model.Role;
import model.Team;
import model.PersonalityType;
import java.util.*;
import java.util.logging.Logger;

public class TeamRebalancer {
    private static final Logger LOGGER = Logger.getLogger(TeamRebalancer.class.getName());
    // Default maximum number of iterations for the rebalancing loop
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    // Default maximum number of swap attempts between teams
    private static final int DEFAULT_MAX_SWAP_ATTEMPTS = 200;

    // Default rebalancing method with standard parameters
    public static void rebalance(List<Team> teams) {
        rebalance(teams, DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_SWAP_ATTEMPTS);
    }

    // Main rebalancing algorithm with configurable parameters
    public static void rebalance(List<Team> teams, int maxIterations, int maxSwapAttempts) {
        // Check for valid input
        if (teams == null || teams.isEmpty()) return;

        boolean improved = true; // Flag to continue rebalancing if improvements are found
        int iter = 0; // Iteration counter
        int totalSwaps = 0; // Total number of successful swaps performed

        // Calculate global average skill across all teams
        double globalAvg = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);

        // Continue rebalancing while improvements are found and iteration limit not reached
        while (improved && iter < Math.max(3, maxIterations)) {
            improved = false;
            iter++;

            // Track swap attempts to prevent infinite loops
            int attempts = 0;
            outer:
            // Compare each pair of teams for potential swaps
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    Team t1 = teams.get(i);
                    Team t2 = teams.get(j);

                    // Skip already well-balanced teams to optimize performance
                    if (isTeamWellBalanced(t1, globalAvg) && isTeamWellBalanced(t2, globalAvg)
                            && satisfiesPersonalityRule(t1) && satisfiesPersonalityRule(t2)
                            && t1.getUniqueRoleCount() >= 3 && t2.getUniqueRoleCount() >= 3) {
                        continue;
                    }

                    // Attempt a smart swap between the two teams
                    if (trySmartSwap(t1, t2, globalAvg)) {
                        improved = true;
                        totalSwaps++;
                        // Recalculate global average after successful swap
                        globalAvg = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(globalAvg);
                    }

                    attempts++;
                    // Stop if maximum swap attempts reached
                    if (attempts > maxSwapAttempts) break outer;
                }
            }
        }

        LOGGER.info("TeamRebalancer finished after " + iter + " iterations, total swaps: " + totalSwaps);
    }

    // Checks if a team is already well-balanced according to all criteria
    private static boolean isTeamWellBalanced(Team t, double globalAvg) {
        if (t.getCurrentSize() == 0) return false;
        // Calculate skill deviation as percentage
        double avg = t.calculateAverageSkill();
        double deviationPct = globalAvg > 0 ? Math.abs(avg - globalAvg) / globalAvg : 0.0;
        // Check all balance criteria
        boolean skillOk = deviationPct <= 0.15; // Within 15% of global average
        boolean rolesOk = t.getUniqueRoleCount() >= 3; // At least 3 unique roles
        boolean personalityOk = satisfiesPersonalityRule(t); // Satisfies personality composition rules
        return skillOk && rolesOk && personalityOk;
    }

    // Attempts to find and execute a beneficial swap between two teams
    private static boolean trySmartSwap(Team a, Team b, double globalAvg) {
        // Get team members as lists for iteration
        List<Participant> aMembers = new ArrayList<>(a.getMembers());
        List<Participant> bMembers = new ArrayList<>(b.getMembers());

        // Collect current roles in each team to identify missing roles
        Set<Role> aRoles = collectRoles(aMembers);
        Set<Role> bRoles = collectRoles(bMembers);

        // Priority 1: Swaps that improve role diversity
        for (Participant pa : aMembers) {
            for (Participant pb : bMembers) {
                boolean pbHelpsA = fillsMissingRole(pb, aRoles);
                boolean paHelpsB = fillsMissingRole(pa, bRoles);
                // Attempt swap if it improves role distribution in either team
                if (pbHelpsA || paHelpsB) {
                    if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) return true;
                }
            }
        }

        // Priority 2: Swaps that fix personality composition issues
        for (Participant pa : aMembers) {
            for (Participant pb : bMembers) {
                // Handle leader distribution
                if (isLeader(pa) && !isLeader(pb)) {
                    // Move leader to team that lacks one
                    if (countByPersonality(b, PersonalityType.LEADER) < 1) {
                        if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) return true;
                    }
                }
                // Handle thinker distribution
                if (isThinker(pa) && countByPersonality(b, PersonalityType.THINKER) < 2) {
                    if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) return true;
                }
            }
        }

        // Priority 3: Swaps that improve skill balance
        double currentSum = Math.abs(a.calculateAverageSkill() - globalAvg) + Math.abs(b.calculateAverageSkill() - globalAvg);
        for (Participant pa : aMembers) {
            for (Participant pb : bMembers) {
                // Attempt any swap and evaluate if it improves or maintains skill balance
                if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) {
                    double newSum = Math.abs(a.calculateAverageSkill() - globalAvg) + Math.abs(b.calculateAverageSkill() - globalAvg);
                    // Accept swap if it doesn't make things worse
                    if (newSum <= currentSum + 0.0001) return true;
                }
            }
        }

        return false;
    }

    // Executes a participant swap between two teams and evaluates the result
    private static boolean attemptSwapAndEvaluate(Team a, Team b, Participant pa, Participant pb, double globalAvg) {
        // Remove participants from their original teams
        boolean removedA = a.removeMember(pa);
        boolean removedB = b.removeMember(pb);
        // Add participants to their new teams
        boolean addedA = a.addMember(pb);
        boolean addedB = b.addMember(pa);

        boolean success = false;
        if (removedA && removedB && addedA && addedB) {
            // Evaluate both teams after the swap
            boolean aOk = evaluateTeamAfterSwap(a, globalAvg);
            boolean bOk = evaluateTeamAfterSwap(b, globalAvg);
            // Only keep swap if both teams are valid
            if (aOk && bOk) {
                success = true;
            }
        }

        // If swap unsuccessful or makes teams invalid, revert changes
        if (!success) {
            // Revert participant movements
            if (addedA) a.removeMember(pb);
            if (addedB) b.removeMember(pa);
            if (removedA) a.addMember(pa);
            if (removedB) b.addMember(pb);
        }
        return success;
    }

    // Evaluates if a team meets all constraints after a swap
    private static boolean evaluateTeamAfterSwap(Team t, double globalAvg) {
        boolean rolesOk = t.getUniqueRoleCount() >= 3; // Minimum 3 unique roles
        boolean personalityOk = satisfiesPersonalityRule(t); // Valid personality composition
        // Skill deviation within 20% of global average
        double deviationPct = globalAvg > 0 ? Math.abs(t.calculateAverageSkill() - globalAvg) / globalAvg : 0.0;
        boolean skillOk = deviationPct <= 0.20;
        return rolesOk && personalityOk && skillOk;
    }

    // Checks if a participant provides a role missing from a team
    private static boolean fillsMissingRole(Participant p, Set<Role> existingRoles) {
        return !existingRoles.contains(p.getPreferredRole());
    }

    // Collects all unique roles from a list of participants
    private static Set<Role> collectRoles(List<Participant> members) {
        Set<Role> set = new HashSet<>();
        for (Participant p : members) set.add(p.getPreferredRole());
        return set;
    }

    // Checks if participant has leader personality
    private static boolean isLeader(Participant p) {
        return p.getPersonalityType() == PersonalityType.LEADER;
    }

    // Checks if participant has thinker personality
    private static boolean isThinker(Participant p) {
        return p.getPersonalityType() == PersonalityType.THINKER;
    }

    // Counts participants with specific personality type in a team
    private static int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == type).count();
    }

    // Checks if team satisfies personality composition rules
    private static boolean satisfiesPersonalityRule(Team t) {
        int leaders = countByPersonality(t, PersonalityType.LEADER);
        int thinkers = countByPersonality(t, PersonalityType.THINKER);
        int size = t.getMaxSize();
        // Different rules based on team size
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
        // Standard rule for teams of 4+: exactly 1 leader, 1-2 thinkers
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }
}
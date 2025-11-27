package service;

import model.Participant;
import model.Role;
import model.Team;
import model.PersonalityType;
import java.util.*;
import java.util.logging.Logger;

public class TeamRebalancer {
    private static final Logger LOGGER = Logger.getLogger(TeamRebalancer.class.getName());
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    private static final int DEFAULT_MAX_SWAP_ATTEMPTS = 200;

    public static void rebalance(List<Team> teams) {
        rebalance(teams, DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_SWAP_ATTEMPTS);
    }

    public static void rebalance(List<Team> teams, int maxIterations, int maxSwapAttempts) {
        if (teams == null || teams.isEmpty()) return;
        boolean improved = true;
        int iter = 0;
        int totalSwaps = 0;


        double globalAvg = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);

        while (improved && iter < Math.max(3, maxIterations)) {
            improved = false;
            iter++;


            int attempts = 0;
            outer:
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    Team t1 = teams.get(i);
                    Team t2 = teams.get(j);

                    if (isTeamWellBalanced(t1, globalAvg) && isTeamWellBalanced(t2, globalAvg)
                            && satisfiesPersonalityRule(t1) && satisfiesPersonalityRule(t2)
                            && t1.getUniqueRoleCount() >= 3 && t2.getUniqueRoleCount() >= 3) {
                        continue;
                    }

                    if (trySmartSwap(t1, t2, globalAvg)) {
                        improved = true;
                        totalSwaps++;
                        globalAvg = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(globalAvg);
                    }

                    attempts++;
                    if (attempts > maxSwapAttempts) break outer;
                }
            }
        }

        LOGGER.info("TeamRebalancer finished after " + iter + " iterations, total swaps: " + totalSwaps);
    }

    private static boolean isTeamWellBalanced(Team t, double globalAvg) {
        if (t.getCurrentSize() == 0) return false;
        double avg = t.calculateAverageSkill();
        double deviationPct = globalAvg > 0 ? Math.abs(avg - globalAvg) / globalAvg : 0.0;
        boolean skillOk = deviationPct <= 0.15;
        boolean rolesOk = t.getUniqueRoleCount() >= 3;
        boolean personalityOk = satisfiesPersonalityRule(t);
        return skillOk && rolesOk && personalityOk;
    }

    private static boolean trySmartSwap(Team a, Team b, double globalAvg) {
        List<Participant> aMembers = new ArrayList<>(a.getMembers());
        List<Participant> bMembers = new ArrayList<>(b.getMembers());

        Set<Role> aRoles = collectRoles(aMembers);
        Set<Role> bRoles = collectRoles(bMembers);

        for (Participant pa : aMembers) {
            for (Participant pb : bMembers) {
                boolean pbHelpsA = fillsMissingRole(pb, aRoles);
                boolean paHelpsB = fillsMissingRole(pa, bRoles);
                if (pbHelpsA || paHelpsB) {
                    if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) return true;
                }
            }
        }

        for (Participant pa : aMembers) {
            for (Participant pb : bMembers) {
                if (isLeader(pa) && !isLeader(pb)) {
                    if (countByPersonality(b, PersonalityType.LEADER) < 1) {
                        if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) return true;
                    }
                }
                if (isThinker(pa) && countByPersonality(b, PersonalityType.THINKER) < 2) {
                    if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) return true;
                }
            }
        }

        double currentSum = Math.abs(a.calculateAverageSkill() - globalAvg) + Math.abs(b.calculateAverageSkill() - globalAvg);
        for (Participant pa : aMembers) {
            for (Participant pb : bMembers) {
                if (attemptSwapAndEvaluate(a, b, pa, pb, globalAvg)) {
                    double newSum = Math.abs(a.calculateAverageSkill() - globalAvg) + Math.abs(b.calculateAverageSkill() - globalAvg);
                    if (newSum <= currentSum + 0.0001) return true;
                }
            }
        }

        return false;
    }

    private static boolean attemptSwapAndEvaluate(Team a, Team b, Participant pa, Participant pb, double globalAvg) {
        boolean removedA = a.removeMember(pa);
        boolean removedB = b.removeMember(pb);
        boolean addedA = a.addMember(pb);
        boolean addedB = b.addMember(pa);

        boolean success = false;
        if (removedA && removedB && addedA && addedB) {
            // Evaluate
            boolean aOk = evaluateTeamAfterSwap(a, globalAvg);
            boolean bOk = evaluateTeamAfterSwap(b, globalAvg);
            if (aOk && bOk) {
                success = true;
            }
        }

        if (!success) {
            // revert
            if (addedA) a.removeMember(pb);
            if (addedB) b.removeMember(pa);
            if (removedA) a.addMember(pa);
            if (removedB) b.addMember(pb);
        }
        return success;
    }

    private static boolean evaluateTeamAfterSwap(Team t, double globalAvg) {
        boolean rolesOk = t.getUniqueRoleCount() >= 3;
        boolean personalityOk = satisfiesPersonalityRule(t);
        double deviationPct = globalAvg > 0 ? Math.abs(t.calculateAverageSkill() - globalAvg) / globalAvg : 0.0;
        boolean skillOk = deviationPct <= 0.20;
        return rolesOk && personalityOk && skillOk;
    }

    private static boolean fillsMissingRole(Participant p, Set<Role> existingRoles) {
        return !existingRoles.contains(p.getPreferredRole());
    }

    private static Set<Role> collectRoles(List<Participant> members) {
        Set<Role> set = new HashSet<>();
        for (Participant p : members) set.add(p.getPreferredRole());
        return set;
    }

    private static boolean isLeader(Participant p) {
        return p.getPersonalityType() == PersonalityType.LEADER;
    }

    private static boolean isThinker(Participant p) {
        return p.getPersonalityType() == PersonalityType.THINKER;
    }

    private static int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == type).count();
    }

    private static boolean satisfiesPersonalityRule(Team t) {
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
}

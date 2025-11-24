package service;

import model.Participant;
import model.PersonalityType;
import model.Team;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class TeamRebalancer {
    private static final Logger LOGGER = Logger.getLogger(TeamRebalancer.class.getName());
    private static final int MAX_ITERATIONS = 500;
    private static final double SKILL_WORSEN_TOLERANCE = 0.5;

    private TeamRebalancer() {}

    public static void rebalance(List<Team> team) {
        if (teams == null || teams.isEmpty()) return;

        double globalAvg = computeGlobalAverage(teams);
        int iter = 0;
        boolean changed;

        do {
            changed = false;
            iter++;

            for (Team t : team) {
                if (!satisfiesPersonalityRule(t)) {
                    boolean didFix = tryFixPersonalityForTeam(t, teams, globalAvg);
                    if (didFix) {
                        changed = true;
                        globalAvg = computeGlobalAverage(teams);
                    }
                }
            }
        } while (changed && iter < MAX_ITERATIONS);

        LOGGER.info("Personality rebalancer finished after " + iter + " iterations");
    }

    private static boolean tryFixPersonalityForTeam(Team team, List<Team> allTeams, double globalAvg) {
        List<Team> others = allTeams.stream().filter(t -> t != team).collect(Collectors.toList());
        List<Participant> teamMembers = new ArrayList<>(team.getMembers());

        for (Participant pFromTeam : teamMembers) {
            for (Team donorTeam : others) {
                List<Participant> donorMembers = new ArrayList<>(donorTeam.getMembers());
                for (Participant pFromDonor : donorMembers) {
                    if (pFromTeam == null || pFromDonor == null) continue;
                    if (pFromTeam.getId().equals(pFromDonor.getId())) continue;

                    swapMembersUnsafe(team, donorTeam, pFromTeam, pFromDonor);

                    boolean teamValid = satisfiesPersonalityRule(team);
                    boolean donorValid = satisfiesPersonalityRule(donorTeam);

                    double beforeScore = computeSkillDeviationScoreWithSwapInverse(team, donorTeam, pFromTeam, pFromDonor, globalAvg);
                    double afterScore = computeSkillDeviationScore(team, donorTeam, globalAvg);

                    if (teamValid && donorValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) {
                        LOGGER.info(String.format("Personality-swap accepted: swapped %s(%s) of %s with %s(%s) of %s",
                                pFromTeam.getName(), pFromTeam.getPersonalityType(), team.getTeamId(),
                                pFromDonor.getName(), pFromDonor.getPersonalityType(), donorTeam.getTeamId()));
                        return true;
                    }

                    swapMembersUnsafe(team, donorTeam, pFromDonor, pFromTeam);

                }
            }
        }

        if (tryTargetedLeaderMove(team, allTeams, globalAvg)) return true;
        if (tryTargetedThinkerMove(team, allTeams, globalAvg)) return true;

        return false;
    }

}


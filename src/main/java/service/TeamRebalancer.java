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

    public static void rebalance(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return;

        double globalAvg = computeGlobalAverage(teams);
        int iter = 0;
        boolean changed;

        do {
            changed = false;
            iter++;

            for (Team t : teams) {
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

    private static boolean tryTargetedLeaderMove(Team target, List<Team> allTeams, double globalAvg) {
        int targetLeaders = countByPersonality(target, PersonalityType.LEADER);
        if (targetLeaders == 1) return false;

        if (targetLeaders == 0) {

            for (Team donor : allTeams) {
                if (donor == target) continue;
                int donorLeaders = countByPersonality(donor, PersonalityType.LEADER);
                if (donorLeaders < 1) continue;
                Optional<Participant> leaderOpt = donor.getMembers().stream()
                        .filter(m -> m.getPersonalityType() == PersonalityType.LEADER)
                        .findFirst();
                if (leaderOpt.isEmpty()) continue;
                Participant leader = leaderOpt.get();

                Optional<Participant> outboundOpt = target.getMembers().stream()
                        .filter(m -> m.getPersonalityType() != PersonalityType.LEADER)
                        .findFirst();
                if (outboundOpt.isEmpty()) continue;
                Participant outbound = outboundOpt.get();

                swapMembersUnsafe(target, donor, outbound, leader);

                boolean targetValid = satisfiesPersonalityRule(target);
                boolean donorValid = satisfiesPersonalityRule(donor);
                double beforeScore = computeSkillDeviationScoreWithSwapInverse(target, donor, outbound, leader, globalAvg);
                double afterScore = computeSkillDeviationScore(target, donor, globalAvg);

                if (targetValid && donorValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) {
                    LOGGER.info(String.format("Leader-move: moved %s from %s to %s", leader.getName(), donor.getTeamId(), target.getTeamId()));
                    return true;
                } else {
                    swapMembersUnsafe(target, donor, leader, outbound);
                }
            }
        }

        if (targetLeaders > 1) {
            List<Participant> extraLeaders = target.getMembers().stream()
                    .filter(m -> m.getPersonalityType() == PersonalityType.LEADER)
                    .collect(Collectors.toList());
            for (Participant extra : extraLeaders) {
                for (Team receiver : allTeams) {
                    if (receiver == target) continue;
                    int recvLeaders = countByPersonality(receiver, PersonalityType.LEADER);
                    if (recvLeaders != 0) continue;

                    Optional<Participant> recvOutboundOpt = receiver.getMembers().stream()
                            .filter(m -> m.getPersonalityType() != PersonalityType.LEADER)
                            .findFirst();
                    if (recvOutboundOpt.isEmpty()) continue;
                    Participant recvOutbound = recvOutboundOpt.get();

                    swapMembersUnsafe(target, receiver, extra, recvOutbound);

                    boolean targetValid = satisfiesPersonalityRule(target);
                    boolean recvValid = satisfiesPersonalityRule(receiver);
                    double beforeScore = computeSkillDeviationScoreWithSwapInverse(target, receiver, extra, recvOutbound, globalAvg);
                    double afterScore = computeSkillDeviationScore(target, receiver, globalAvg);

                    if (targetValid && recvValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) {
                        LOGGER.info(String.format("Leader-redistribute: moved extra leader %s from %s to %s", extra.getName(), target.getTeamId(), receiver.getTeamId()));
                        return true;
                    } else {
                        swapMembersUnsafe(target, receiver, recvOutbound, extra); // revert
                    }
                }
            }
        }
        return false;
    }
    private static boolean tryTargetedThinkerMove(Team target, List<Team> allTeams, double globalAvg) {
        int targetThinkers = countByPersonality(target, PersonalityType.THINKER);
        if (targetThinkers >= 1 && targetThinkers <= 2) return false;

        if (targetThinkers == 0) {
            for (Team donor : allTeams) {
                if (donor == target) continue;
                int donorThinkers = countByPersonality(donor, PersonalityType.THINKER);
                if (donorThinkers <= 1) continue;
                Optional<Participant> donorThinkerOpt = donor.getMembers().stream()
                        .filter(m -> m.getPersonalityType() == PersonalityType.THINKER)
                        .findFirst();
                if (donorThinkerOpt.isEmpty()) continue;
                Participant donorThinker = donorThinkerOpt.get();

                Optional<Participant> outboundOpt = target.getMembers().stream()
                        .filter(m -> m.getPersonalityType() != PersonalityType.THINKER)
                        .findFirst();
                if (outboundOpt.isEmpty()) continue;
                Participant outbound = outboundOpt.get();

                swapMembersUnsafe(target, donor, outbound, donorThinker);

                boolean targetValid = satisfiesPersonalityRule(target);
                boolean donorValid = satisfiesPersonalityRule(donor);
                double beforeScore = computeSkillDeviationScoreWithSwapInverse(target, donor, outbound, donorThinker, globalAvg);
                double afterScore = computeSkillDeviationScore(target, donor, globalAvg);

                if (targetValid && donorValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) {
                    LOGGER.info(String.format("Thinker-move: moved %s from %s to %s", donorThinker.getName(), donor.getTeamId(), target.getTeamId()));
                    return true;
                } else {
                    swapMembersUnsafe(target, donor, donorThinker, outbound); // revert
                }
            }
        }

        if (targetThinkers > 2) {
            List<Participant> extraThinkers = target.getMembers().stream()
                    .filter(m -> m.getPersonalityType() == PersonalityType.THINKER)
                    .collect(Collectors.toList());
            for (Participant extra : extraThinkers) {
                for (Team receiver : allTeams) {
                    if (receiver == target) continue;
                    int recvThinkers = countByPersonality(receiver, PersonalityType.THINKER);
                    if (recvThinkers >= 2) continue;
                    Optional<Participant> recvOutboundOpt = receiver.getMembers().stream()
                            .filter(m -> m.getPersonalityType() != PersonalityType.THINKER)
                            .findFirst();
                    if (recvOutboundOpt.isEmpty()) continue;
                    Participant recvOutbound = recvOutboundOpt.get();

                    swapMembersUnsafe(target, receiver, extra, recvOutbound);

                    boolean targetValid = satisfiesPersonalityRule(target);
                    boolean recvValid = satisfiesPersonalityRule(receiver);
                    double beforeScore = computeSkillDeviationScoreWithSwapInverse(target, receiver, extra, recvOutbound, globalAvg);
                    double afterScore = computeSkillDeviationScore(target, receiver, globalAvg);

                    if (targetValid && recvValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) {
                        LOGGER.info(String.format("Thinker-redistribute: moved extra thinker %s from %s to %s", extra.getName(), target.getTeamId(), receiver.getTeamId()));
                        return true;
                    } else {
                        swapMembersUnsafe(target, receiver, recvOutbound, extra); // revert
                    }
                }
            }
        }

        return false;
    }

    private static boolean satisfiesPersonalityRule(Team t) {
        int leaders = countByPersonality(t, PersonalityType.LEADER);
        int thinkers = countByPersonality(t, PersonalityType.THINKER);
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }

    private static int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream()
                .filter(m -> m.getPersonalityType() == type)
                .count();
    }

    private static double computeGlobalAverage(List<Team> teams) {
        return teams.stream()
                .mapToDouble(Team::calculateAverageSkill)
                .average()
                .orElse(0.0);
    }

    private static double computeSkillDeviationScore(Team a, Team b, double globalAvg) {
        double devA = Math.abs(a.calculateAverageSkill() - globalAvg);
        double devB = Math.abs(b.calculateAverageSkill() - globalAvg);
        return devA + devB;
    }

    private static double computeSkillDeviationScoreWithSwapInverse(Team a, Team b, Participant pa, Participant pb, double globalAvg) {
        return computeSkillDeviationScore(a, b, globalAvg);
    }

    private static void swapMembersUnsafe(Team a, Team b, Participant pa, Participant pb) {
        a.removeMember(pa);
        b.removeMember(pb);
        a.addMember(pb);
        b.addMember(pa);
    }
}













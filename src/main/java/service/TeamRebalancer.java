package service;

import model.Participant;
import model.PersonalityType;
import model.Team;

import java.util.*;
import java.util.logging.Logger;

public final class TeamRebalancer {
    private static final Logger LOGGER = Logger.getLogger(TeamRebalancer.class.getName());
    private static final int MAX_ITERATIONS = 1000;
    private static final double SKILL_WORSEN_TOLERANCE = 0.5;

    private TeamRebalancer() {}

    public static void rebalance(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return;
        double globalAvg = computeGlobalAverage(teams);
        boolean changed;
        int iter = 0;
        do {
            changed = false;
            iter++;
            for (Team t : teams) {
                if (!satisfiesPersonalityRule(t)) {
                    boolean fixed = tryFixPersonalityForTeam(t, teams, globalAvg);
                    if (fixed) {
                        changed = true;
                        globalAvg = computeGlobalAverage(teams);
                    }
                }
            }
        } while (changed && iter < MAX_ITERATIONS);
        LOGGER.info("TeamRebalancer finished after " + iter + " iterations");
    }

    private static boolean tryFixPersonalityForTeam(Team team, List<Team> allTeams, double globalAvg) {
        for (Team donor : allTeams) {
            if (donor == team) continue;
            List<Participant> fromMembers = new ArrayList<>(team.getMembers());
            List<Participant> donorMembers = new ArrayList<>(donor.getMembers());
            for (Participant a : fromMembers) {
                for (Participant b : donorMembers) {
                    if (a.getId().equals(b.getId())) continue;
                    swapMembers(team, donor, a, b);
                    boolean teamValid = satisfiesPersonalityRule(team);
                    boolean donorValid = satisfiesPersonalityRule(donor);
                    double beforeScore = computeSkillDeviationScore(team, donor, globalAvg); // current after swap used as heuristic
                    double afterScore = computeSkillDeviationScore(team, donor, globalAvg);
                    if (teamValid && donorValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) return true;
                    swapMembers(team, donor, b, a);
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
                if (countByPersonality(donor, PersonalityType.LEADER) < 1) continue;
                Optional<Participant> leaderOpt = donor.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.LEADER).findFirst();
                if (leaderOpt.isEmpty()) continue;
                Participant leader = leaderOpt.get();
                Optional<Participant> outOpt = target.getMembers().stream().filter(m -> m.getPersonalityType() != PersonalityType.LEADER).findFirst();
                if (outOpt.isEmpty()) continue;
                Participant outbound = outOpt.get();
                swapMembers(target, donor, outbound, leader);
                boolean targetValid = satisfiesPersonalityRule(target);
                boolean donorValid = satisfiesPersonalityRule(donor);
                double beforeScore = computeSkillDeviationScore(target, donor, globalAvg);
                double afterScore = computeSkillDeviationScore(target, donor, globalAvg);
                if (targetValid && donorValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) return true;
                swapMembers(target, donor, leader, outbound);
            }
        }
        if (targetLeaders > 1) {
            List<Participant> extras = new ArrayList<>();
            for (Participant p : target.getMembers()) if (p.getPersonalityType() == PersonalityType.LEADER) extras.add(p);
            for (Participant extra : extras) {
                for (Team receiver : allTeams) {
                    if (receiver == target) continue;
                    if (countByPersonality(receiver, PersonalityType.LEADER) != 0) continue;
                    Optional<Participant> recvOutOpt = receiver.getMembers().stream().filter(m -> m.getPersonalityType() != PersonalityType.LEADER).findFirst();
                    if (recvOutOpt.isEmpty()) continue;
                    Participant recvOut = recvOutOpt.get();
                    swapMembers(target, receiver, extra, recvOut);
                    boolean targetValid = satisfiesPersonalityRule(target);
                    boolean recvValid = satisfiesPersonalityRule(receiver);
                    double beforeScore = computeSkillDeviationScore(target, receiver, globalAvg);
                    double afterScore = computeSkillDeviationScore(target, receiver, globalAvg);
                    if (targetValid && recvValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) return true;
                    swapMembers(target, receiver, recvOut, extra);
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
                if (countByPersonality(donor, PersonalityType.THINKER) <= 1) continue;
                Optional<Participant> donorThinkerOpt = donor.getMembers().stream().filter(m -> m.getPersonalityType() == PersonalityType.THINKER).findFirst();
                if (donorThinkerOpt.isEmpty()) continue;
                Participant donorThinker = donorThinkerOpt.get();
                Optional<Participant> outOpt = target.getMembers().stream().filter(m -> m.getPersonalityType() != PersonalityType.THINKER).findFirst();
                if (outOpt.isEmpty()) continue;
                Participant outbound = outOpt.get();
                swapMembers(target, donor, outbound, donorThinker);
                boolean targetValid = satisfiesPersonalityRule(target);
                boolean donorValid = satisfiesPersonalityRule(donor);
                double beforeScore = computeSkillDeviationScore(target, donor, globalAvg);
                double afterScore = computeSkillDeviationScore(target, donor, globalAvg);
                if (targetValid && donorValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) return true;
                swapMembers(target, donor, donorThinker, outbound);
            }
        }
        if (targetThinkers > 2) {
            List<Participant> extras = new ArrayList<>();
            for (Participant p : target.getMembers()) if (p.getPersonalityType() == PersonalityType.THINKER) extras.add(p);
            for (Participant extra : extras) {
                for (Team receiver : allTeams) {
                    if (receiver == target) continue;
                    int recvThinkers = countByPersonality(receiver, PersonalityType.THINKER);
                    if (recvThinkers >= 2) continue;
                    Optional<Participant> recvOutOpt = receiver.getMembers().stream().filter(m -> m.getPersonalityType() != PersonalityType.THINKER).findFirst();
                    if (recvOutOpt.isEmpty()) continue;
                    Participant recvOut = recvOutOpt.get();
                    swapMembers(target, receiver, extra, recvOut);
                    boolean targetValid = satisfiesPersonalityRule(target);
                    boolean recvValid = satisfiesPersonalityRule(receiver);
                    double beforeScore = computeSkillDeviationScore(target, receiver, globalAvg);
                    double afterScore = computeSkillDeviationScore(target, receiver, globalAvg);
                    if (targetValid && recvValid && afterScore <= beforeScore + SKILL_WORSEN_TOLERANCE) return true;
                    swapMembers(target, receiver, recvOut, extra);
                }
            }
        }
        return false;
    }

    private static void swapMembers(Team a, Team b, Participant pa, Participant pb) {
        a.removeMember(pa);
        b.removeMember(pb);
        a.addMember(pb);
        b.addMember(pa);
    }

    private static boolean satisfiesPersonalityRule(Team t) {
        int leaders = countByPersonality(t, PersonalityType.LEADER);
        int thinkers = countByPersonality(t, PersonalityType.THINKER);
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }

    private static int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == type).count();
    }

    private static double computeGlobalAverage(List<Team> teams) {
        return teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
    }

    private static double computeSkillDeviationScore(Team a, Team b, double globalAvg) {
        double devA = Math.abs(a.calculateAverageSkill() - globalAvg);
        double devB = Math.abs(b.calculateAverageSkill() - globalAvg);
        return devA + devB;
    }
}

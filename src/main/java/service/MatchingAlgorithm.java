package service;

import model.*;
import exception.TeamFormationException;
import java.util.*;
import java.util.logging.Logger;

public class MatchingAlgorithm {
    private static final Logger LOGGER = Logger.getLogger(MatchingAlgorithm.class.getName());
    private static final int MAX_SAME_GAME = 2;
    private static final int MIN_ROLES = 3;

    public static List<Team> matchParticipants(List<Participant> participants, int teamSize)
            throws TeamFormationException {

        if (participants.size() < teamSize * 2) {
            throw new TeamFormationException(
                    "Need at least " + (teamSize * 2) + " participants for balanced teams");
        }

        int numTeams = participants.size() / teamSize;
        List<Team> teams = initializeTeams(numTeams, teamSize);

        Map<PersonalityType, List<Participant>> byPersonality =
                groupByPersonality(participants);

        distributeLeaders(teams, byPersonality.get(PersonalityType.LEADER));

        distributeThinkers(teams, byPersonality.get(PersonalityType.THINKER), teamSize);

        distributeBalanced(teams, byPersonality.get(PersonalityType.BALANCED));

        optimizeTeams(teams);

        validateTeams(teams);

        return teams;
    }

    private static List<Team> initializeTeams(int numTeams, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) {
            teams.add(new Team("T" + (i + 1), "Team " + (i + 1), teamSize));
        }
        return teams;
    }

    private static Map<PersonalityType, List<Participant>> groupByPersonality(
            List<Participant> participants) {
        Map<PersonalityType, List<Participant>> groups = new EnumMap<>(PersonalityType.class);

        for (PersonalityType type : PersonalityType.values()) {
            groups.put(type, new ArrayList<>());
        }

        for (Participant p : participants) {
            groups.get(p.getPersoalityType()).add(p);
        }

        Random random = new Random();
        for (List<Participant> group : groups.values()) {
            Collections.shuffle(group, random);
        }
        return groups;
    }

    private static void distributeLeaders(List<Team> teams, List<Participant> leaders)
            throws TeamFormationException {
        if (leaders.size() < teams.size()) {
            LOGGER.warning("Not enough leaders for all teams, using available leaders");
        }

        for (int i = 0; i < Math.min(teams.size(), leaders.size()); i++) {
            teams.get(i).addMember(leaders.get(i));
        }
    }

    private static void distributeThinkers(List<Team> teams, List<Participant> thinkers, int teamSize) {
        int thinkersPerTeam = teamSize <= 5 ? 1 : 2;
        int thinkerIndex = 0;

        for (Team team : teams) {
            int assigned = 0;
            while (assigned < thinkersPerTeam && thinkerIndex < thinkers.size()) {
                if (canAddToTeam(team, thinkers.get(thinkerIndex))) {
                    team.addMember(thinkers.get(thinkerIndex));
                    assigned++;
                }
                thinkerIndex++;
            }
        }
    }
    private static  void distributeBalanced(List<Team> teams, List<Participant> balanced) {
        int participantIndex = 0;

        while (participantIndex < balanced.size()) {
            for (Team team : teams) {
                if (!team.isFull() && participantIndex < balanced.size()) {
                    Participant p = balanced.get(participantIndex);
                    if (canAddToTeam(team, p)) {
                        team.addMember(p);
                        participantIndex++;
                    } else {
                        participantIndex++;
                    }
                }
            }
        }
    }

    private static boolean canAddToTeam(Team team, Participant participant) {
        if (team.countByGame(participant.getPreferredGame()) >= MAX_SAME_GAME) {
            return false;
        }
        return true;
    }
    private static void optimizeTeams(List<Team> teams) {

        for (int iteration = 0; iteration < 10; iteration++) {
            boolean improved = false;

            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    if (trySwapMembers(teams.get(i), teams.get(j))) {
                        improved = true;
                    }
                }
            }

            if (!improved) break;
        }
    }
    private static boolean trySwapMembers(Team team1, Team team2) {
        List<Participant> members1 = team1.getMembers();
        List<Participant> members2 = team2.getMembers();

        for (Participant p1 : members1) {
            for (Participant p2 : members2) {

                team1.removeMember(p1);
                team2.removeMember(p2);
                team1.addMember(p2);
                team1.addMember(p1);

                if (isSwapBetter(team1, team2)) {
                    return true;
                } else {

                    team1.removeMember(p2);
                    team2.removeMember(p1);
                    team1.addMember(p1);
                    team2.addMember(p2);
                }
            }
        }
        return false;
    }

    private static boolean isSwapBetter(Team team1, Team team2) {
        return team1.getUniqueRoleCount() >= MIN_ROLES &&
                team2.getUniqueRoleCount() >= MIN_ROLES;
    }

    private static void validateTeams(List<Team> teams) throws TeamFormationException {
        for (Team team : teams) {
            if(team.getUniqueRoleCount() < MIN_ROLES) {
                LOGGER.warning(team.getTeamId() + " has fewer than " + MIN_ROLES + " roles");
            }
        }

        double globalAverage = teams.stream()
                .mapToDouble(Team::calculateAverageSkill)
                .average()
                .orElse(0.0);

        for (Team team : teams) {
            double deviation = Math.abs(team.calculateAverageSkill() - globalAverage);
            if (deviation > globalAverage * 0.20) {
                LOGGER.warning(team.getTeamId() + " has skill imbalance");
            }
        }
    }

}

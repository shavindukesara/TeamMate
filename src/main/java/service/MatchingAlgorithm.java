package service;

import model.*;
import exception.TeamFormationException;

import java.util.*;
import java.util.logging.Logger;

public class MatchingAlgorithm {
    private static final Logger LOGGER = Logger.getLogger(MatchingAlgorithm.class.getName());
    private static final int MAX_SAME_GAME = 2;
    private static final int MIN_ROLES = 3;
    private static final Scanner SCANNER = new Scanner(System.in);

    public static List<Team> matchParticipants(List<Participant> participants, int teamSize)
            throws TeamFormationException {

        if (participants == null || participants.isEmpty()) throw new TeamFormationException("No participants provided");
        if (teamSize < 1) throw new TeamFormationException("Team size must be >= 1");

        int numTeams = participants.size() / teamSize;
        if (numTeams == 0) throw new TeamFormationException("Team size too large for participant count");

        List<Team> teams = initializeTeams(numTeams, teamSize);

        Map<PersonalityType, List<Participant>> byPersonality = groupByPersonality(participants);

        List<Participant> leaders = byPersonality.getOrDefault(PersonalityType.LEADER, new ArrayList<>());
        List<Participant> thinkers = byPersonality.getOrDefault(PersonalityType.THINKER, new ArrayList<>());
        List<Participant> balanced = byPersonality.getOrDefault(PersonalityType.BALANCED, new ArrayList<>());

        for (Team team : teams) {
            assignForTeam(team, teamSize, leaders, thinkers, balanced);
        }

        List<Participant> leftovers = new ArrayList<>();
        leftovers.addAll(leaders);
        leftovers.addAll(thinkers);
        leftovers.addAll(balanced);

        optimizeTeams(teams);

        TeamRebalancer.rebalance(teams);

        List<Team> violating = new ArrayList<>();
        for (Team t : teams) {
            if (teamViolates(t)) violating.add(t);
        }

        if (!violating.isEmpty()) {
            for (Team vt : violating) {
                for (Participant p : vt.getMembers()) leftovers.add(p);
            }
            teams.removeAll(violating);
            LOGGER.info("Removed " + violating.size() + " unstable team(s) and moved their members to leftovers");
        }

        if (!leftovers.isEmpty()) {
            System.out.println("\nThere are " + leftovers.size() + " remaining participants after forming valid teams.");
            System.out.println("What would you like to do with them?");
            System.out.println("1) Create additional teams from remaining participants (may be unbalanced) - WARNING");
            System.out.println("2) Do NOT create additional teams (remaining participants will be left unteamed)");
            System.out.print("Choose: ");
            String choice = SCANNER.nextLine().trim();
            if ("1".equals(choice)) {
                List<Team> extra = createExtraTeamsFromLeftovers(leftovers, teamSize, teams.size());
                teams.addAll(extra);
                if (!extra.isEmpty()) LOGGER.warning("Created " + extra.size() + " extra team(s) from leftovers (may be unbalanced)");
            } else {
                LOGGER.info("User chose not to create extra teams for leftover participants");
            }
        }

        validateTeams(teams);

        return teams;
    }

    private static List<Team> createExtraTeamsFromLeftovers(List<Participant> leftovers, int teamSize, int existingCount) {
        List<Team> extras = new ArrayList<>();
        Collections.shuffle(leftovers, new Random());
        int index = 0;
        int teamCounter = existingCount;
        while (index < leftovers.size()) {
            int remaining = leftovers.size() - index;
            int currentSize = Math.min(teamSize, remaining);
            Team t = new Team("XT" + (++teamCounter), "Extra Team " + teamCounter, currentSize);
            for (int i = 0; i < currentSize; i++) {
                Participant p = leftovers.get(index++);
                t.addMember(p);
            }
            extras.add(t);
            if (currentSize < teamSize) LOGGER.warning(t.getTeamId() + " created with size " + currentSize + " (smaller than desired team size)");
        }
        return extras;
    }

    private static void assignForTeam(Team team, int teamSize,
                                      List<Participant> leaders,
                                      List<Participant> thinkers,
                                      List<Participant> balanced) {

        if (teamSize == 1) {
            Participant leader = pollCandidate(leaders, team);
            if (leader != null) { team.addMember(leader); return; }
            Participant thinker = pollCandidate(thinkers, team);
            if (thinker != null) { team.addMember(thinker); return; }
            Participant bal = pollCandidate(balanced, team);
            if (bal != null) team.addMember(bal);
            return;
        }

        if (teamSize == 2) {
            Participant leader = pollCandidate(leaders, team);
            if (leader != null) {
                team.addMember(leader);
                Participant thinker = pollCandidate(thinkers, team);
                if (thinker != null) { team.addMember(thinker); return; }
                Participant bal = pollCandidate(balanced, team);
                if (bal != null) { team.addMember(bal); return; }
                Participant thinkerAny = pollAny(thinkers);
                if (thinkerAny != null) { team.addMember(thinkerAny); return; }
                Participant balAny = pollAny(balanced);
                if (balAny != null) { team.addMember(balAny); return; }
                return;
            } else {
                Participant thinkerOnly = pollCandidate(thinkers, team);
                if (thinkerOnly != null) {
                    team.addMember(thinkerOnly);
                    Participant bal = pollCandidate(balanced, team);
                    if (bal != null) { team.addMember(bal); LOGGER.warning(team.getTeamId() + " had no leader; formed with thinker + balanced"); return; }
                    Participant balAny = pollAny(balanced);
                    if (balAny != null) { team.addMember(balAny); LOGGER.warning(team.getTeamId() + " had no leader; formed with thinker + balanced (fallback)"); return; }
                }
                Participant balAny = pollAny(balanced);
                if (balAny != null) team.addMember(balAny);
                Participant any = pollAny(leaders);
                if (any != null) team.addMember(any);
                return;
            }
        }

        Participant leader = pollCandidate(leaders, team);
        if (leader != null) team.addMember(leader);

        int targetThinkers;
        if (teamSize == 3) targetThinkers = 2;
        else targetThinkers = Math.min(2, Math.max(1, teamSize / 4));

        int assignedThinkers = 0;
        while (assignedThinkers < targetThinkers && !thinkers.isEmpty() && !team.isFull()) {
            Participant t = pollCandidate(thinkers, team);
            if (t == null) break;
            team.addMember(t);
            assignedThinkers++;
        }

        while (!team.isFull() && !balanced.isEmpty()) {
            Participant b = pollCandidate(balanced, team);
            if (b == null) break;
            team.addMember(b);
        }

        while (!team.isFull()) {
            Participant any = pollAnyFromPools(leaders, thinkers, balanced);
            if (any == null) break;
            team.addMember(any);
        }
    }

    private static Participant pollAnyFromPools(List<Participant> leaders, List<Participant> thinkers, List<Participant> balanced) {
        if (!leaders.isEmpty()) return pollAny(leaders);
        if (!thinkers.isEmpty()) return pollAny(thinkers);
        if (!balanced.isEmpty()) return pollAny(balanced);
        return null;
    }

    private static Participant pollAny(List<Participant> list) {
        if (list == null || list.isEmpty()) return null;
        return list.remove(0);
    }

    private static Participant pollCandidate(List<Participant> list, Team team) {
        if (list == null || list.isEmpty()) return null;
        for (int i = 0; i < list.size(); i++) {
            Participant p = list.get(i);
            if (canAddToTeam(team, p)) {
                list.remove(i);
                return p;
            }
        }
        return list.remove(0);
    }

    private static List<Team> initializeTeams(int numTeams, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) teams.add(new Team("T" + (i + 1), "Team " + (i + 1), teamSize));
        return teams;
    }

    private static Map<PersonalityType, List<Participant>> groupByPersonality(
            List<Participant> participants) {
        Map<PersonalityType, List<Participant>> groups = new EnumMap<>(PersonalityType.class);
        for (PersonalityType type : PersonalityType.values()) groups.put(type, new ArrayList<>());
        for (Participant p : participants) groups.get(p.getPersonalityType()).add(p);
        Random random = new Random();
        for (List<Participant> group : groups.values()) Collections.shuffle(group, random);
        return groups;
    }

    private static boolean canAddToTeam(Team team, Participant participant) {
        if (team.countByGame(participant.getPreferredGame()) >= MAX_SAME_GAME) return false;
        return true;
    }

    private static void optimizeTeams(List<Team> teams) {
        for (int iteration = 0; iteration < 10; iteration++) {
            boolean improved = false;
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    if (trySwapMembers(teams.get(i), teams.get(j))) improved = true;
                }
            }
            if (!improved) break;
        }
    }

    private static boolean trySwapMembers(Team team1, Team team2) {
        List<Participant> members1 = new ArrayList<>(team1.getMembers());
        List<Participant> members2 = new ArrayList<>(team2.getMembers());
        for (Participant p1 : members1) {
            for (Participant p2 : members2) {
                if (p1 == null || p2 == null) continue;
                if (p1.getId().equals(p2.getId())) continue;
                team1.removeMember(p1);
                team2.removeMember(p2);
                boolean addedToT1 = team1.addMember(p2);
                boolean addedToT2 = team2.addMember(p1);
                if (!(addedToT1 && addedToT2)) {
                    if (addedToT1) team1.removeMember(p2);
                    if (addedToT2) team2.removeMember(p1);
                    team1.addMember(p1);
                    team2.addMember(p2);
                    continue;
                }
                if (isSwapBetter(team1, team2)) return true;
                team1.removeMember(p2);
                team2.removeMember(p1);
                team1.addMember(p1);
                team2.addMember(p2);
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
            if (team.getUniqueRoleCount() < MIN_ROLES) LOGGER.warning(team.getTeamId() + " has fewer than " + MIN_ROLES + " roles");
        }
        double globalAverage = teams.stream().mapToDouble(Team::calculateAverageSkill).average().orElse(0.0);
        for (Team team : teams) {
            double deviation = Math.abs(team.calculateAverageSkill() - globalAverage);
            if (globalAverage > 0 && deviation > globalAverage * 0.20) LOGGER.warning(team.getTeamId() + " has skill imbalance");
            if (!satisfiesPersonalityRule(team)) LOGGER.warning(team.getTeamId() + " violates personality rule (exactly 1 leader; 1-2 thinkers)");
        }
    }

    private static boolean teamViolates(Team t) {
        if (t.getUniqueRoleCount() < MIN_ROLES) return true;
        if (!satisfiesPersonalityRule(t)) return true;
        return false;
    }

    private static boolean satisfiesPersonalityRule(Team t) {
        int leaders = countByPersonality(t, PersonalityType.LEADER);
        int thinkers = countByPersonality(t, PersonalityType.THINKER);
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }

    private static int countByPersonality(Team t, PersonalityType type) {
        return (int) t.getMembers().stream().filter(m -> m.getPersonalityType() == type).count();
    }
}

package model;

import java.util.*;
import java.util.stream.Collectors;

public class Team {
    private int teamID;
    private String teamName;
    private List<Participant> members;
    private int maxSize;

    public Team(String teamId, String teamName, int maxSize) {
        this.teamID = teamId;
        this.teamName = teamName;
        this.maxSize = maxSize;
        this.members = new ArrayList<>();
    }

    public boolean addMember(Participant participant) {
        if (members.size() >= maxSize) {
            return false;
        }
        return members.add(participant);
    }

    public boolean removeMember(Participant participant) {
        return members.remove(participant);
    }

    public double calculateAverageSkill() {
        if (members.isEmpty()) return 0.0;
        return members.stream()
                .mapToInt(Participant::getSkillLevel)
                .average()
                .orElse(0.0);
    }

    public Map<String, Integer> getGameDistribution() {
        return members.stream()
                .collect(Collectors.groupingBy(
                        Participant::getPreferredGame,
                        Collectors.summingInt(p -> 1)
                ));
    }

    public Map<PersonalityType, Integer> getPersonalityDistribution() {
        return members.stream()
                .collect(Collectors.groupingBy(
                        Participant::getPersoalityType,
                        Collectors.summingInt(p -> 1)
                ));
    }

    public int getUniqueRoleCount() {
        return (int) members.stream()
                .map(Participant::getPreferredRole)
                .distinct()
                .count();
    }

    public int countByGame(String game) {
        return (int) members.stream()
                .filter(p -> p.getPreferredGame().equals(game))
                .count();
    }

    public int countByPersonalityType(PersonalityType type) {
        return (int) members.stream()
                .filter(p -> p.getPersoalityType() == type)
                .count();
    }

    public boolean isFull() {
        return members.size() == maxSize;
    }

    public boolean isBalanced() {
        return checkGameDiversity() && checkRoleDiversity() && checkPersonalityMix();
    }

    private boolean checkGameDiversity() {
        Map<String, Integer> gameDist = getGameDistribution();
        return gameDist.values().stream().allMatch(count -> count <= 2);
    }
    private boolean checkRoleDiversity() {
        return getUniqueRoleCount() >= 3;
    }

    private boolean checkPersonalityMix() {
        int leaders = countByPersonalityType(PersonalityType.LEADER);
        int thinkers = countByPersonalityType(PersonalityType.THINKER);
        return leaders == 1 && thinkers >= 1 && thinkers <= 2;
    }

    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public List<Participant> getMembers() { return new ArrayList<>(members); }
    public int getMaxSize() { return maxSize; }
    public int getCurrentSize() { return members.size(); }

    @Override
    public String toString() {
        return String.format("Team{teamID=%s, name=%s, members=%d/%d, avgSkill=%.2f}",
                teamID, teamName, members.size(), maxSize, calculateAverageSkill());
    }
}


package model;

import java.util.*;

public class Team {
    private final String teamId;
    private int teamID;
    private String teamName;
    private List<Participant> members;
    private int maxSize;

    public Team(String teamId, String teamName, int maxSize) {
        this.teamId = teamId;
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

    public boolean isFull() {
        return members.size() == maxSize;
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


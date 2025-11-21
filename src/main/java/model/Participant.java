package model;

import java.util.Objects;

public class Participant {
    private String id;
    private String name;
    private String email;
    private String preferredGame;
    private int skillLevel;
    private Role preferredRole;
    private int persoalityScore;
    private PersonalityType persoalityType;

    public Participant(String id, String name, String emailt) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public Participant(String id, String name, String email, String preferredGame,
                       int skillLevel, Role preferredRole, int persoalityScore) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.preferredGame = preferredGame;
        this.skillLevel = skillLevel;
        this.preferredRole = preferredRole;
        this.persoalityScore = persoalityScore;
        this.personalityType = PersonalityType.fromScore(persoalityScore);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPreferredGame() { return preferredGame; }
    public void setPreferredGame(String preferredGame) { this.preferredGame = preferredGame; }

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }

    public Role getPreferredRole() { return preferredRole; }
    public void setPreferredRole(Role preferredRole) { this.preferredRole = preferredRole; }

    public int getPersoalityScore() { return persoalityScore; }
    public void setPersoalityScore(int persoalityScore) {
        this.persoalityScore = persoalityScore;
        this.persoalityType = PersonalityType.fromScore(persoalityScore);
    }

    public PersonalityType getPersoalityType() { return persoalityType; }
    public void setPersoalityType(PersonalityType persoalityType) {
        this.persoalityType = persoalityType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Participant that = (Participant) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Participant{id='%s', name='%s', game='%s', skill='%s', role='%s', type='%s'}",
                id, name, preferredGame, skillLevel, preferredRole, persoalityType);
    }
}

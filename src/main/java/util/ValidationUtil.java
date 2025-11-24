package util;

import model.Role;

import java.util.regex.Pattern;

public class ValidationUtil {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private static final String[] VALID_GAMES = {
            "Chess", "FIFA", "Basketball", "CS:GO", "DOTA 2", "Valorant", "Badminton"};

    public static boolean validateEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    public static boolean validateSkillLevel(String skillLevel) {
        return skillLevel >= 1 && skillLevel <= 10;
    }
    public static boolean validatePersonalityScore(int score) {
        return score >= 0 && score <= 100;
    }
    public static boolean validateRole(String role) {
        try {
            Role.fromString(role);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean validateGame(String game) {
        for (String validGame : VALID_GAMES) {
            if (validGame.equalsIgnoreCase(game)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validateTeamSize(int size, int totalparticipants) {
        return size >= 3 && size <= 10 && totalparticipants >= size * 2;
    }
}

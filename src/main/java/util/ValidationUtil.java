package util;

import java.util.regex.Pattern;

public class ValidationUtil {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final String[] VALID_GAMES = {
            "CS:GO", "Basketball", "Valorant", "Chess", "DOTA 2", "FIFA"
    };

    public static boolean validateEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean validateSkillLevel(int skillLevel) {
        return skillLevel >= 1 && skillLevel <= 10;
    }

    public static boolean validatePersonalityScore(int score) {
        return score >= 0 && score <= 100;
    }

    public static boolean validateGame(String game) {
        if (game == null) return false;
        for (String validGame : VALID_GAMES) {
            if (validGame.equalsIgnoreCase(game)) {
                return true;
            }
        }
        return false;
    }

    public static String getValidGame(String input) {
        if (input == null) return null;
        for (String validGame : VALID_GAMES) {
            if (validGame.equalsIgnoreCase(input)) {
                return validGame; // Return the properly capitalized version
            }
        }
        return null;
    }
}

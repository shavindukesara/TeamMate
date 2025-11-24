package service;

import model.PersonalityType;
import java.util.List;
import java.util.Scanner;

public class Questionnaire {
    private static final List<String> QUESTIONS = List.of(
            "Q1 I ;;enjoy taking the lead and guiding others during group activities.",
            "Q2 I prefer analyzing situations and coming up with strategic solutions.",
            "Q3 I work well with others and enjoy collaborative teamwork.",
            "Q4 I am calm under pressure and can help maintain team morale.",
            "Q5 I like making quick decisions and adapting in dynamic situations."
    );

    public int runSurveyAndGetScore(Scanner scanner) {
        System.out.println("Please rate each statement from 1 (Strongly Disagree) to 5 (Strongly Agree).");
        int total = 0;
        for (int i = 0; i < QUESTIONS.size(); i++) {
            int ans = 0;
            while (ans < 1 || ans > 5) {
                System.out.println(QUESTIONS.get(i));
                System.out.print("Your answer (1-5): ");
                String line = scanner.nextLine().trim();
                try {
                    ans = Integer.parseInt(line);
                    if (ans < 1 || ans > 5) {
                        System.out.println("Please enter a number between 1 and 5.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Enter a number 1-5.");
                }
            }
            total += ans;
        }
        int scaled = total * 4;
        scaled = Math.max(0, Math.min(100, scaled));
        System.out.println("Survey complete. Scaled personality score: " + scaled);
        System.out.println("Personality type: " + PersonalityType.fromScore(scaled).getDisplayName());
        return scaled;
    }
}

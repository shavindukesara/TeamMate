package service;

import model.PersonalityType;

import java.util.List;
import java.util.Scanner;

public class Questionnaire {

    private static final List<String> QUESTIONS = List.of(
            "Q1 I enjoy taking the lead and guiding others during group activities.",
            "Q2 I prefer analyzing situations and coming up with strategic solutions.",
            "Q3 I work well with others and enjoy collaborative teamwork.",
            "Q4 I am calm under pressure and can help maintain team morale.",
            "Q5 I like making quick decisions and adapting in dynamic situations."
    );


    public int runSurveyAndGetScaledScore(Scanner scanner) {
        System.out.println("\nPlease rate each statement from 1 (Strongly Disagree) to 5 (Strongly Agree).");
        int total = 0;
        for (String question : QUESTIONS) {
            int ans = 0;
            while (ans < 1 || ans > 5) {
                System.out.println(question);
                System.out.print("Your answer (1-5): ");
                String line = scanner.nextLine().trim();
                try {
                    ans = Integer.parseInt(line);
                    if (ans < 1 || ans > 5) System.out.println("Enter a number between 1 and 5.");
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Enter 1-5.");
                }
            }
            total += ans;
        }

        int scaled = Math.round(50 + (total - 5) * 2.5f);
        scaled = Math.max(50, Math.min(100, scaled));

        PersonalityType type = PersonalityType.fromScore(scaled);
        System.out.println("\nSurvey complete. Scaled score: " + scaled + " (" + type.getDisplayName() + ")");
        return scaled;
    }
}

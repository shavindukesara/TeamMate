package service;

import model.*;
import java.util.List;

public class PersonalityClassifier {

    public static PersonalityType classifyPErsonality(int score) {
        return PersonalityType.fromScore(score);
    }

    public static int caclculateScode(int[] responses) {
        int total = 0;
        for (int response : responses) {
            total += response;
        }
        return total * 4;
    }

    public static void classifyAllParticipants(List<Participant> participants) {
        for (Participant p : participants) {
            PersonalityType type = classifyPErsonality(p.getPersonalityScore());
            p.setPersonalityType(type);
        }
    }
}

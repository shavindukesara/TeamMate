package concurrent;

import model.Participant;
import util.ValidationUtil;
import exception.InvalidDataException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class DataProcessorTask implements Callable<List<Participant>> {
    private static final Logger LOGGER = Logger.getLogger(DataProcessorTask.class.getName());
    private final List<String[]> dataChunk;
    private final int startIndex;

    public DataProcessorTask(List<String[]> dataChunk, int startIndex) {
        this.dataChunk = dataChunk;
        this.startIndex = startIndex;
    }

    @Override
    public List<Participant> call() {
        List<Participant> participants = new ArrayList<>();
        int lineNumber = startIndex;

        for (String[] fields : dataChunk) {
            lineNumber++;
            try {
                Participant p = parseParticipant(fields);
                if (validateParticipants(p)) {
                    participants.add(p);
                    LOGGER.fine("Thread " + Thread.currentThread().getName() +
                            " processed line " + lineNumber);

                }
            } catch (Exception e) {
                LOGGER.warning("Failed to process line " + lineNumber + ": " + e.getMessage());
            }
        }
        return participants;
    }
    private Participant parseParticipant(String[] fields) throws InvalidDataException {
        if (fields.length < 7) {
            throw new InvalidDataException("Insufficient fields");
        }
        String id = fields[0].trim();
        String name = fields[1].trim();
        String email = fields[2].trim();
        String game = fields[3].trim();
        int skill = Integer.parseInt(fields[4].trim());
        String roleStr = fields[5].trim();
        int score = Integer.parseInt(fields[6].trim());

        return new Participant(id, name, email, game, skill,
                model.Role.fromString(roleStr), score);
    }
    private boolean validateParticipants(Participant p) {
        return ValidationUtil.validateEmail(p.getEmail()) &&
        ValidationUtil.validateSkillLevel(p.getSkillLevel()) &&
                ValidationUtil.validatePersonalityScore(p.getPersonalityScore());
    }

}

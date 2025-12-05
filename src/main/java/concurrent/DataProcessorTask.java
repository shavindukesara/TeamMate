package concurrent;

import model.Participant;
import util.ValidationUtil;
import exception.InvalidDataException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class DataProcessorTask implements Callable<List<Participant>> {
    private static final Logger LOGGER = Logger.getLogger(DataProcessorTask.class.getName());
    private final List<String[]> dataChunk;  // Assigned slice of CSV data for this task
    private final int startIndex;            // Starting position in the original file

    public DataProcessorTask(List<String[]> dataChunk, int startIndex) {
        this.dataChunk = dataChunk;
        this.startIndex = startIndex;
    }

    @Override
    public List<Participant> call() {
        List<Participant> participants = new ArrayList<>();  // Collected valid participants
        int lineNumber = startIndex;  // Track position in original file for logging

        // Process each CSV line in the assigned chunk
        for (String[] fields : dataChunk) {
            lineNumber++;
            try {
                Participant p = parseParticipant(fields);

                // Validate before adding to results
                if (validateParticipants(p)) {
                    participants.add(p);

                    // Log successful processing at fine detail level
                    LOGGER.fine("Thread " + Thread.currentThread().getName() +
                            " processed line " + lineNumber);
                }
            } catch (Exception e) {
                // Log failures but continue processing remaining lines
                LOGGER.warning("Failed to process line " + lineNumber + ": " + e.getMessage());
            }
        }
        return participants;  // Return successfully processed participants
    }

    // Convert CSV fields to Participant object
    private Participant parseParticipant(String[] fields) throws InvalidDataException {
        // Verify required fields are present
        if (fields.length < 7) {
            throw new InvalidDataException("Insufficient fields");
        }

        // Extract and clean each field
        String id = fields[0].trim();
        String name = fields[1].trim();
        String email = fields[2].trim();
        String game = fields[3].trim();
        int skill = Integer.parseInt(fields[4].trim());
        String roleStr = fields[5].trim();
        int score = Integer.parseInt(fields[6].trim());

        // Construct Participant with extracted data
        return new Participant(id, name, email, game, skill,
                model.Role.fromString(roleStr), score);
    }

    // Apply validation rules to participant data
    private boolean validateParticipants(Participant p) {
        return ValidationUtil.validateEmail(p.getEmail()) &&
                ValidationUtil.validateSkillLevel(p.getSkillLevel()) &&
                ValidationUtil.validatePersonalityScore(p.getPersonalityScore());
    }
}
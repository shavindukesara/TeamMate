package util;

import model.Participant;
import model.Role;
import model.Team;
import exception.InvalidDataException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CSVHandler {
    private static final Logger LOGGER = Logger.getLogger(CSVHandler.class.getName());

    public static List<Participant> loadParticipants(String filePath)
            throws IOException, InvalidDataException {

        List<Participant> participants = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Read header line
            String headerLine = br.readLine();

            // YOUR CODE GOES HERE (inside the method)
            if (headerLine == null || !validateHeaders(headerLine)) {
                throw new InvalidDataException("Invalid CSV headers");
            }

            // Read data lines
            String line;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    Participant participant = parseLine(line);
                    if (validateParticipant(participant)) {
                        participants.add(participant);
                    } else {
                        errors.add("Line " + lineNumber + ": Invalid data");
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                    LOGGER.warning("Failed to parse line " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        if (!errors.isEmpty()) {
            LOGGER.warning("Loaded with " + errors.size() + " errors");
        }

        LOGGER.info("Successfully loaded " + participants.size() + " participants");
        return participants;
    }


    private static boolean validateHeaders(String headerLine) {
        String[] expectedHeaders = {
                "ID", "Name", "Email", "PreferredGame", "SkillLevel",
                "PreferredRole", "PersonalityScore", "PersonalityType"
        };
        String[] headers = headerLine.split(",");

        if (headers.length != expectedHeaders.length) {
            return false;
        }

        for (int i = 0; i < expectedHeaders.length; i++) {
            if (!headers[i].trim().equals(expectedHeaders[i])) {
                return false;
            }
        }
        return true;
    }


    private static Participant parseLine(String line) throws InvalidDataException {
        String[] fields = line.split(",");

        if (fields.length < 8) {
            throw new InvalidDataException("Insufficient fields in CSV line");
        }

        try {
            String id = fields[0].trim();
            String name = fields[1].trim();
            String email = fields[2].trim();
            String preferredGame = fields[3].trim();
            int skillLevel = Integer.parseInt(fields[4].trim());
            Role preferredRole = Role.fromString(fields[5].trim());
            int personalityScore = Integer.parseInt(fields[6].trim());

            return new Participant(id, name, email, preferredGame,
                    skillLevel, preferredRole, personalityScore);
        } catch (NumberFormatException e) {
            throw new InvalidDataException("Invalid number format in CSV line");
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException("Invalid enum value in CSV line");
        }
    }


    private static boolean validateParticipant(Participant p) {
        return ValidationUtil.validateEmail(p.getEmail()) &&
                ValidationUtil.validateSkillLevel(p.getSkillLevel()) &&
                ValidationUtil.validatePersonalityScore(p.getPersonalityScore()) &&
                ValidationUtil.validateGame(p.getPreferredGame());
    }


    public static void saveTeams(List<Team> teams, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {

            writer.println("TeamID,TeamName,ParticipantID,ParticipantName," +
                    "Email,PreferredGame,SkillLevel,Role,PersonalityType");


            for (Team team : teams) {
                for (Participant member : team.getMembers()) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%d,%s,%s%n",
                            team.getTeamId(),
                            team.getTeamName(),
                            member.getId(),
                            member.getName(),
                            member.getEmail(),
                            member.getPreferredGame(),
                            member.getSkillLevel(),
                            member.getPreferredRole(),
                            member.getPersonalityType()
                    );
                }
            }

            LOGGER.info("Successfully saved " + teams.size() + " teams to " + filePath);
        }
    }
}

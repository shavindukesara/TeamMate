package util;

import model.Participant;
import model.Role;
import model.Team;
import model.PersonalityType;
import exception.InvalidDataException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Map;
import java.util.LinkedHashMap;

public class CSVHandler {
    private static final Logger LOGGER = Logger.getLogger(CSVHandler.class.getName());

    private static final String[] EXPECTED_HEADERS = {
            "ID", "Name", "Email", "PreferredGame", "SkillLevel",
            "PreferredRole", "PersonalityScore", "PersonalityType"
    };

    public static List<Participant> loadParticipants(String filePath)
            throws IOException, InvalidDataException {

        List<Participant> participants = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null || !validateHeaders(headerLine)) {
                throw new InvalidDataException("Invalid CSV headers in " + filePath);
            }

            String line;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    String[] fields = parseCSVLine(line);
                    Participant participant = parseFieldsToParticipant(fields);
                    if (validateParticipant(participant)) {
                        participants.add(participant);
                    } else {
                        errors.add("Line " + lineNumber + ": Validation failed");
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                    LOGGER.warning("Failed to parse line " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        if (!errors.isEmpty()) {
            LOGGER.warning("Loaded with " + errors.size() + " errors");
            for (String err : errors) LOGGER.warning(err);
        }

        LOGGER.info("Successfully loaded " + participants.size() + " participants from " + filePath);
        return participants;
    }

    /**
     * Load teams from CSV file
     * Expected format: TeamID,TeamName,ParticipantID,ParticipantName,Email,PreferredGame,SkillLevel,Role,PersonalityType
     */
    public static List<Team> loadTeamsFromCSV(String filePath) throws IOException {
        Map<String, Team> teamsMap = new LinkedHashMap<>();

        File file = new File(filePath);
        if (!file.exists()) {
            LOGGER.warning("Teams file does not exist: " + filePath);
            return new ArrayList<>();
        }

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                LOGGER.warning("Teams file is empty: " + filePath);
                return new ArrayList<>();
            }

            String line;
            int lineNumber = 1;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    // Use the proper CSV parser instead of simple split
                    String[] fields = parseCSVLine(line);

                    if (fields.length < 9) {
                        LOGGER.warning("Line " + lineNumber + ": Insufficient fields (found " + fields.length + ", expected 9)");
                        continue;
                    }

                    String teamId = fields[0].trim();
                    String teamName = fields[1].trim();
                    String participantId = fields[2].trim();
                    String participantName = fields[3].trim();
                    String email = fields[4].trim();
                    String preferredGame = fields[5].trim();

                    // Parse skill level
                    int skillLevel;
                    try {
                        skillLevel = Integer.parseInt(fields[6].trim());
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Line " + lineNumber + ": Invalid skill level: " + fields[6]);
                        continue;
                    }

                    // Parse role
                    Role role;
                    try {
                        role = Role.fromString(fields[7].trim());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Line " + lineNumber + ": Invalid role: " + fields[7]);
                        continue;
                    }

                    // Parse personality score
                    int personalityScore;
                    try {
                        personalityScore = Integer.parseInt(fields[8].trim());
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Line " + lineNumber + ": Invalid personality score: " + fields[8]);
                        continue;
                    }

                    // Get or create team
                    Team team = teamsMap.get(teamId);
                    if (team == null) {
                        team = new Team(teamId, teamName, 10); // Default max size 10
                        teamsMap.put(teamId, team);
                    }

                    // Create and add participant
                    Participant participant = new Participant(
                            participantId, participantName, email, preferredGame,
                            skillLevel, role, personalityScore
                    );

                    boolean added = team.addMember(participant);
                    if (!added) {
                        LOGGER.warning("Line " + lineNumber + ": Failed to add participant " + participantId + " to team " + teamId);
                    }

                } catch (Exception e) {
                    LOGGER.warning("Line " + lineNumber + ": Failed to parse - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        List<Team> teams = new ArrayList<>(teamsMap.values());
        LOGGER.info("Successfully loaded " + teams.size() + " teams with total " +
                teams.stream().mapToInt(Team::getCurrentSize).sum() + " participants from " + filePath);

        return teams;
    }

    private static boolean validateHeaders(String headerLine) {
        String[] headers = parseCSVLine(headerLine);
        if (headers.length != EXPECTED_HEADERS.length) return false;
        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            if (!EXPECTED_HEADERS[i].equalsIgnoreCase(headers[i].trim())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Properly parse CSV line handling quotes and escaped commas
     */
    private static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // Handle escaped quotes ("")
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    private static Participant parseFieldsToParticipant(String[] fields) throws InvalidDataException {
        if (fields.length < 7) {
            throw new InvalidDataException("Insufficient fields in CSV line (expected at least 7)");
        }

        try {
            String id = fields[0].trim();
            String name = fields[1].trim();
            String email = fields[2].trim();
            String preferredGame = fields[3].trim();
            int skillLevel = Integer.parseInt(fields[4].trim());
            Role preferredRole = Role.fromString(fields[5].trim());
            int personalityScore = Integer.parseInt(fields[6].trim());

            PersonalityType pType = null;
            if (fields.length >= 8) {
                String typeField = fields[7].trim();
                if (!typeField.isEmpty()) {
                    try {
                        pType = PersonalityType.valueOf(typeField.toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid personality type
                    }
                }
            }

            Participant p = new Participant(id, name, email, preferredGame, skillLevel, preferredRole, personalityScore);

            if (pType != null) {
                try {
                    p.setPersonalityType(pType);
                } catch (NoSuchMethodError | AbstractMethodError ignored) {
                    // Method doesn't exist or can't be called
                }
            }

            return p;
        } catch (NumberFormatException e) {
            throw new InvalidDataException("Invalid number format in CSV line: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException("Invalid enum or value in CSV line: " + e.getMessage());
        }
    }

    private static boolean validateParticipant(Participant p) {
        return ValidationUtil.validateEmail(p.getEmail()) &&
                ValidationUtil.validateSkillLevel(p.getSkillLevel()) &&
                ValidationUtil.validatePersonalityScore(p.getPersonalityScore()) &&
                ValidationUtil.validateGame(p.getPreferredGame());
    }

    public static boolean appendParticipantToCsv(Participant p, String filePath) throws IOException {
        Path file = Paths.get(filePath);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Set<String> existingIds = new HashSet<>();
        Set<String> existingEmails = new HashSet<>();
        if (Files.exists(file)) {
            try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String header = br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    String[] fields = parseCSVLine(line);
                    if (fields.length >= 3) {
                        String id = fields[0].trim();
                        String email = fields[2].trim().toLowerCase();
                        if (!id.isEmpty()) existingIds.add(id);
                        if (!email.isEmpty()) existingEmails.add(email);
                    }
                }
            } catch (IOException e) {
                LOGGER.warning("Failed to read existing participants for duplicate check: " + e.getMessage());
            }
        }

        if (p.getId() != null && existingIds.contains(p.getId())) {
            LOGGER.info("Duplicate participant detected by ID: " + p.getId());
            return false;
        }
        if (p.getEmail() != null && existingEmails.contains(p.getEmail().toLowerCase())) {
            LOGGER.info("Duplicate participant detected by email: " + p.getEmail());
            return false;
        }

        boolean writeHeader = !Files.exists(file) || Files.size(file) == 0;
        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            if (writeHeader) {
                bw.write(String.join(",", EXPECTED_HEADERS));
                bw.newLine();
            }

            String roleStr = p.getPreferredRole() != null ? p.getPreferredRole().name() : "";
            String typeStr = p.getPersonalityType() != null ? p.getPersonalityType().name() : "";

            String line = String.format("%s,%s,%s,%s,%d,%s,%d,%s",
                    escapeCsv(p.getId()),
                    escapeCsv(p.getName()),
                    escapeCsv(p.getEmail()),
                    escapeCsv(p.getPreferredGame()),
                    p.getSkillLevel(),
                    escapeCsv(roleStr),
                    p.getPersonalityScore(),
                    escapeCsv(typeStr)
            );
            bw.write(line);
            bw.newLine();
        }

        LOGGER.info("Appended participant " + p.getId() + " to " + filePath);
        return true;
    }

    public static void saveTeams(List<Team> teams, String filePath) throws IOException {
        Path file = Paths.get(filePath);
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (PrintWriter writer = new PrintWriter(new FileWriter(file.toFile(), false))) {
            writer.println("TeamID,TeamName,ParticipantID,ParticipantName,Email,PreferredGame,SkillLevel,Role,PersonalityType");

            for (Team team : teams) {
                for (Participant member : team.getMembers()) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%d,%s,%s%n",
                            escapeCsv(team.getTeamId()),
                            escapeCsv(team.getTeamName()),
                            escapeCsv(member.getId()),
                            escapeCsv(member.getName()),
                            escapeCsv(member.getEmail()),
                            escapeCsv(member.getPreferredGame()),
                            member.getSkillLevel(),
                            escapeCsv(member.getPreferredRole() != null ? member.getPreferredRole().name() : ""),
                            escapeCsv(member.getPersonalityType() != null ? member.getPersonalityType().name() : "")
                    );
                }
            }
            LOGGER.info("Successfully saved " + teams.size() + " teams to " + filePath);
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
    }
}
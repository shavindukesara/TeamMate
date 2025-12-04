package controller;

import model.Participant;
import model.Role;
import repository.ParticipantRepository;
import service.Questionnaire;
import service.TeamFormationStrategy;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class ParticipantAuthController extends BaseController {
    private final ParticipantRepository repository;
    private final TeamFormationStrategy strategy;
    private final Questionnaire questionnaire = new Questionnaire();

    private static final Path ACCOUNTS = Paths.get("data", "participant_accounts.csv");
    private static final Path PARTICIPANTS_CSV = Paths.get("data", "participants_sample.csv");

    public ParticipantAuthController(Scanner scanner, ParticipantRepository repository, TeamFormationStrategy strategy) {
        super(scanner);  // Use parent constructor
        this.repository = repository;
        this.strategy = strategy;
        try { Files.createDirectories(ACCOUNTS.getParent()); } catch (IOException ignored) {}
        if (!Files.exists(ACCOUNTS)) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(ACCOUNTS.toFile(), false))) {
                pw.println("username,password,participantId");
            } catch (IOException ignored) {}
        }
    }

    public ParticipantController launchParticipantSession() {
        while (true) {
            System.out.println("\n" + "=".repeat(55));
            System.out.println("PARTICIPANT AUTH");
            System.out.println("=".repeat(55));
            System.out.println("1 - Login");
            System.out.println("2 - Register");
            System.out.println("3 - Back");
            System.out.print("\nYour choice: ");
            String opt = scanner.nextLine().trim();

            switch (opt) {
                case "1" -> {
                    var p = loginParticipant();
                    if (p != null) return new ParticipantController(scanner, repository, strategy, p);
                }
                case "2" -> {
                    var p = registerParticipant();
                    if (p != null) return new ParticipantController(scanner, repository, strategy, p);
                }
                case "3" -> {
                    return null;
                }
                default -> System.out.println("Invalid selection. Press 1, 2 or 3.");
            }
        }
    }

    private Participant loginParticipant() {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = (System.console() != null) ? new String(System.console().readPassword()) : scanner.nextLine();

        try (BufferedReader br = new BufferedReader(new FileReader(ACCOUNTS.toFile()))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = parseCsvLine(line);
                if (f.length < 3) continue;
                String fileUser = unquote(f[0]);
                String filePass = unquote(f[1]);
                String pid = unquote(f[2]);
                if (fileUser.equals(username) && filePass.equals(password)) {
                    try {
                        var all = repository.load(PARTICIPANTS_CSV.toString());
                        for (Participant p : all) if (p.getId().equals(pid)) return p;
                    } catch (Exception ignored) {}
                    return findParticipantById(pid);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read participant accounts: " + e.getMessage());
        }

        System.out.println("Invalid credentials.");
        return null;
    }

    private Participant registerParticipant() {
        try {
            System.out.print("Choose username: ");
            String username = scanner.nextLine().trim();
            if (username.isEmpty()) { System.out.println("Username required."); return null; }
            if (accountExists(username)) { System.out.println("Username already taken."); return null; }

            System.out.print("Choose password: ");
            String password = (System.console() != null) ? new String(System.console().readPassword()) : scanner.nextLine();

            System.out.print("Full name: ");
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) { System.out.println("Name required."); return null; }

            System.out.print("Email (eg: username@gmail.com): ");
            String email = scanner.nextLine().trim();

            System.out.print("Preferred game (CS:GO, Basketball, Valorant, Chess, DOTA 2, FIFA): ");
            String game = scanner.nextLine().trim();

            System.out.print("Skill (1-10): ");
            int skill;
            try { skill = Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Invalid skill."); return null; }

            System.out.print("Preferred role (Strategist, Attacker, Defender, Supporter, Coordinator): ");
            String roleStr = scanner.nextLine().trim();
            Role role;
            try { role = Role.fromString(roleStr); } catch (Exception e) { System.out.println("Invalid role."); return null; }

            int scaled = questionnaire.runSurveyAndGetScaledScore(scanner);
            String newId = generateNextParticipantId();

            Participant p = new Participant(newId, name, email, game, skill, role, scaled);

            boolean appended = false;
            try {
                appended = repository.append(p, PARTICIPANTS_CSV.toString());
            } catch (Exception ignored) {}

            if (!appended) {
                if (!rawAppendParticipant(p)) {
                    System.err.println("Failed to record participant.");
                    return null;
                }
            }

            try (PrintWriter pw = new PrintWriter(new FileWriter(ACCOUNTS.toFile(), true))) {
                pw.printf("%s,%s,%s%n", escapeCsv(username), escapeCsv(password), escapeCsv(p.getId()));
            }

            System.out.println("Registered successfully (ID: " + p.getId() + ").");
            return p;
        } catch (Exception e) {
            System.err.println("Registration failed: " + e.getMessage());
            return null;
        }
    }

    private boolean accountExists(String username) {
        try (BufferedReader br = new BufferedReader(new FileReader(ACCOUNTS.toFile()))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = parseCsvLine(line);
                if (f.length < 1) continue;
                if (unquote(f[0]).equals(username)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private boolean rawAppendParticipant(Participant p) {
        try {
            Files.createDirectories(PARTICIPANTS_CSV.getParent());
            boolean exists = Files.exists(PARTICIPANTS_CSV);
            try (PrintWriter pw = new PrintWriter(new FileWriter(PARTICIPANTS_CSV.toFile(), true))) {
                if (!exists) {
                    pw.println("ID,Name,Email,PreferredGame,SkillLevel,PreferredRole,PersonalityScore,PersonalityType");
                }
                String line = String.format("%s,%s,%s,%s,%d,%s,%d,%s",
                        escapeCsv(p.getId()),
                        escapeCsv(p.getName()),
                        escapeCsv(p.getEmail()),
                        escapeCsv(p.getPreferredGame()),
                        p.getSkillLevel(),
                        p.getPreferredRole() != null ? escapeCsv(p.getPreferredRole().name()) : "",
                        p.getPersonalityScore(),
                        p.getPersonalityType() != null ? escapeCsv(p.getPersonalityType().name()) : ""
                );
                pw.println(line);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Participant findParticipantById(String id) {
        if (!Files.exists(PARTICIPANTS_CSV)) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(PARTICIPANTS_CSV.toFile()))) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                String[] f = parseCsvLine(line);
                if (f.length < 1) continue;
                String pid = unquote(f[0]);
                if (!pid.equals(id)) continue;
                String name = f.length > 1 ? unquote(f[1]) : "";
                String email = f.length > 2 ? unquote(f[2]) : "";
                String game = f.length > 3 ? unquote(f[3]) : "";
                int skill = f.length > 4 && !unquote(f[4]).isEmpty() ? Integer.parseInt(unquote(f[4])) : 0;
                Role role = null;
                if (f.length > 5 && !unquote(f[5]).isEmpty()) {
                    try { role = Role.fromString(unquote(f[5])); } catch (Exception ignored) {}
                }
                int score = f.length > 6 && !unquote(f[6]).isEmpty() ? Integer.parseInt(unquote(f[6])) : 0;
                return new Participant(pid, name, email, game, skill, role, score);
            }
        } catch (IOException ignored) {}
        return null;
    }

    private String generateNextParticipantId() {
        int max = 0;
        if (Files.exists(PARTICIPANTS_CSV)) {
            try (BufferedReader br = new BufferedReader(new FileReader(PARTICIPANTS_CSV.toFile()))) {
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    String[] f = parseCsvLine(line);
                    if (f.length < 1) continue;
                    String id = unquote(f[0]);
                    if (id.startsWith("P")) {
                        try {
                            int n = Integer.parseInt(id.substring(1));
                            if (n > max) max = n;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException ignored) {}
        }
        return String.format("P%03d", max + 1);
    }

    private static String[] parseCsvLine(String line) {
        if (line == null) return new String[0];
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static String unquote(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}

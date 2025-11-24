package service;

import model.User;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

import static com.sun.media.sound.ModelByteBuffer.loadAll;

public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final Path USERS_FILE = Paths.get("data", "users.csv");

    public AuthService() {
        try {
            Files.createDirectories(USERS_FILE.getParent());
            if (!Files.exists(USERS_FILE)) {
                try (BufferedWriter bw = Files.newBufferedWriter(USERS_FILE)) {
                    bw.write("username,passwordHash");
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize users file: " + e.getMessage());
        }
    }

    public boolean register(String username, String plainPassword) throws IOException {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) return false;
        Map<String, String> map = loadAll();
        if (map.containsKey(username)) return false; // already exists

        String hash = sha256Hex(plainPassword);
        try (BufferedWriter bw = Files.newBufferedWriter(USERS_FILE, StandardOpenOption.APPEND)) {
            bw.write(username + "," + hash);
            bw.newLine();
        }
        LOGGER.info("Registered user: " + username);
        return true;
    }

    public boolean login(String username, String plainPassword) throws IOException {
        Map<String, String> map = loadAll();
        String stored = map.get(username);
        if (stored == null) return false;
        String hash = sha256Hex(plainPassword);
        return stored.equals(hash);
    }

    private Map<String, String> loadAll() throws IOException {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(USERS_FILE)) {
            String header = br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}

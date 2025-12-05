package service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    // Path to the CSV file that stores user credentials
    private static final Path USERS_FILE = Paths.get("data", "users.csv");

    // Constructor initializes the authentication service
    public AuthService() {
        try {
            // Create the data directory if it doesn't exist
            Files.createDirectories(USERS_FILE.getParent());
            // Create the users file with header if it doesn't exist
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

    // Registers a new user with username and password
    public boolean register(String username, String plainPassword) throws IOException {
        // Validate input parameters
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) return false;

        // Load all existing users
        Map<String, String> all = loadAll();
        // Check if username already exists
        if (all.containsKey(username)) return false;

        // Hash the password before storing
        String hash = sha256Hex(plainPassword);
        // Append new user to the CSV file
        try (BufferedWriter bw = Files.newBufferedWriter(USERS_FILE, StandardOpenOption.APPEND)) {
            bw.write(username + "," + hash);
            bw.newLine();
        }
        LOGGER.info("Registered user: " + username);
        return true;
    }

    // Authenticates a user with username and password
    public boolean login(String username, String plainPassword) throws IOException {
        // Load all users
        Map<String, String> all = loadAll();
        // Get stored hash for the username
        String stored = all.get(username);
        // If username doesn't exist, authentication fails
        if (stored == null) return false;
        // Hash the provided password and compare with stored hash
        String hash = sha256Hex(plainPassword);
        return stored.equals(hash);
    }

    // Loads all users from the CSV file into a map
    private Map<String, String> loadAll() throws IOException {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(USERS_FILE)) {
            br.readLine(); // Skip header line
            String line;
            // Read each line and parse username and password hash
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    // Computes SHA-256 hash of a string and returns it as hexadecimal
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            // Convert byte array to hexadecimal string
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
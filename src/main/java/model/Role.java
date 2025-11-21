package model;

public enum Role {
    STRATEGIST("Strategist"),
    ATTACKER("Attacker"),
    DEFENDER("Defender"),
    SUPPORTER("Suppoter"),
    COORDINATOR("Coordinator");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }
    public String getDisplayName() { return displayName; }

    public static Role fromString(String role) {
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(role)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + role);
    }
}

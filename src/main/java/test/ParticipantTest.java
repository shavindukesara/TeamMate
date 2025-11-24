package test;

import model.*;
import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParticipantTest {

    @Test
    public void testparticipantCreation() {
        Participant p = new Participant("P001", "Test User", "test@university.edu");
        assertNotNull(p);
        assertEquals("P001", p.getId());
        assertEquals("Test User", p.getName());
    }

    @Test
    public void testPersonalityClassification() {
        Participant p = new Participant("P001", "Leader User", "leader@university.edu",
                "Chess", 5, Role.STRATEGIST, 95);

        assertEquals(PersonalityType.LEADER, p.getPersonalityType());
    }

    @Test
    public void testSkillLevelValidation() {
        Participant p = new Participant("P001", "Test", "test@university.edu");
        p.setSkillLevel(5);

        assertTrue(p.getSkillLevel() >= 1 && p.getSkillLevel() <= 10);
    }
}

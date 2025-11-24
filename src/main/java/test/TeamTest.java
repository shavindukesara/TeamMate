package test;

import model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class TeamTest {

    @Test
    public void testTeamCreation() {
        Team team = new Team("T1", "Team Alpha", 5);
        assertNotNull(team);
        assertEquals(0, team.getCurrentSize());
        assertEquals(5, team.getMaxSize());
    }

    @Test
    public void testAddMember() {
        Team team = new Team("T1", "Team Alpha", 5);
        Participant p = new Participant("P001", "Test", "test@university.edu",
                "Chess", 5, Role.STRATEGIST, 95);

        assertTrue(team.addMember(p));
        assertEquals(1, team.getCurrentSize());
    }

    @Test
    public void testTeamFull() {
        Team team = new Team("T1", "Team Alpha", 2);
        Participant p1 = new Participant("P001", "Test1", "test1@university.edu",
                "Chess", 5, Role.STRATEGIST, 95);
        Participant p2 = new Participant("P002", "Test2", "test2@university.edu",
                "FIFA", 6, Role.ATTACKER, 80);
        Participant p3 = new Participant("P003", "Test3", "test3@university.edu",
                "DOTA 2", 7, Role.DEFENDER, 70);

        team.addMember(p1);
        team.addMember(p2);
        assertTrue(team.isFull());
        assertFalse(team.addMember(p3)); // Should fail
    }

    @Test
    public void testAverageSkillCalculation() {
        Team team = new Team("T1", "Team Alpha", 3);
        team.addMember(new Participant("P001", "Test1", "t1@university.edu",
                "Chess", 5, Role.STRATEGIST, 95));
        team.addMember(new Participant("P002", "Test2", "t2@university.edu",
                "FIFA", 7, Role.ATTACKER, 80));
        team.addMember(new Participant("P003", "Test3", "t3@university.edu",
                "DOTA 2", 3, Role.DEFENDER, 70));

        assertEquals(5.0, team.calculateAverageSkill(), 0.01);
    }
}

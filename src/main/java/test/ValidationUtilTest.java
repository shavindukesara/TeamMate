package test;

import util.ValidationUtil;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationUtilTest {

    @Test
    public void testEmailValidation() {
        assertTrue(ValidationUtil.validateEmail("user@university.edu"));
        assertTrue(ValidationUtil.validateEmail("test.user@example.com"));
        assertFalse(ValidationUtil.validateEmail("invalid-email"));
        assertFalse(ValidationUtil.validateEmail("@university.edu"));
    }

    @Test
    public void testSkillLevelValidation() {
        assertTrue(ValidationUtil.validateSkillLevel(1));
        assertTrue(ValidationUtil.validateSkillLevel(5));
        assertTrue(ValidationUtil.validateSkillLevel(10));
        assertFalse(ValidationUtil.validateSkillLevel(0));
        assertFalse(ValidationUtil.validateSkillLevel(11));
    }

    @Test
    public void testPersonalityScoreValidation() {
        assertTrue(ValidationUtil.validatePersonalityScore(50));
        assertTrue(ValidationUtil.validatePersonalityScore(75));
        assertTrue(ValidationUtil.validatePersonalityScore(100));
        assertFalse(ValidationUtil.validatePersonalityScore(19));
        assertFalse(ValidationUtil.validatePersonalityScore(101));
    }

}

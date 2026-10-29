package cn.labmanage;

import cn.labmanage.auth.CampusIdentityRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CampusIdentityRulesTest {
    @Test void validatesStudentNumberFormat() {
        assertTrue(CampusIdentityRules.isValid("202601001", "STUDENT"));
        assertFalse(CampusIdentityRules.isValid("20260001", "STUDENT"));
        assertFalse(CampusIdentityRules.isValid("2026010001", "STUDENT"));
    }

    @Test void validatesStaffNumberFormat() {
        assertTrue(CampusIdentityRules.isValid("2026001", "STAFF"));
        assertFalse(CampusIdentityRules.isValid("T2026001", "STAFF"));
        assertFalse(CampusIdentityRules.isValid("20260001", "STAFF"));
    }
}

package cn.labmanage;

import cn.labmanage.repair.RepairStateRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepairStateRulesTest {
    @Test void allowsDefinedWorkflow() {
        assertTrue(RepairStateRules.canTransition("OPEN","PROCESSING"));
        assertTrue(RepairStateRules.canTransition("PROCESSING","WAIT_ACCEPTANCE"));
        assertTrue(RepairStateRules.canTransition("WAIT_ACCEPTANCE","COMPLETED"));
    }
    @Test void rejectsIllegalWorkflow() {
        assertFalse(RepairStateRules.canTransition("OPEN","COMPLETED"));
        assertFalse(RepairStateRules.canTransition("COMPLETED","PROCESSING"));
    }
}

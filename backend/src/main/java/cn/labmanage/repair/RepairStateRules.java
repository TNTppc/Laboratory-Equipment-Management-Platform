package cn.labmanage.repair;

import java.util.Set;

public final class RepairStateRules {
    private RepairStateRules() {}
    public static boolean canTransition(String current, String next) {
        if (current == null || next == null) return false;
        return switch (current) {
            case "OPEN" -> Set.of("PROCESSING", "REJECTED").contains(next);
            case "PROCESSING" -> Set.of("WAIT_ACCEPTANCE", "COMPLETED").contains(next);
            case "WAIT_ACCEPTANCE" -> Set.of("PROCESSING", "COMPLETED").contains(next);
            default -> false;
        };
    }
}

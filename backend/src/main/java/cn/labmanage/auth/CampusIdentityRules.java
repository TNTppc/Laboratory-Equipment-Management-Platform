package cn.labmanage.auth;

public final class CampusIdentityRules {
    private CampusIdentityRules() {}

    public static boolean isValid(String identityNo, String personType) {
        if (identityNo == null || personType == null) return false;
        return switch (personType) {
            case "STUDENT" -> identityNo.matches("\\d{9}");
            case "STAFF" -> identityNo.matches("\\d{7}");
            default -> false;
        };
    }

    public static String message(String personType) {
        return "STUDENT".equals(personType)
            ? "学生学号格式应为：4位入学年份 + 2位班级编码 + 3位编号，例如 202601001"
            : "教职工工号格式应为：4位入职年份 + 3位进校流水号，例如 2026001";
    }
}

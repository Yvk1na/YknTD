package cn.ykntd.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlayerMessagePolicy {
    private static final Pattern POWER_STAGE_NAME = Pattern.compile(
            "(?:前期|中期|后期)\\s*[·・:：\\-—|｜]*\\s*");

    private PlayerMessagePolicy() {
    }

    public static String sanitizeTemplate(String path, String configured, String fallback) {
        String candidate = configured == null ? fallback : configured;
        if (!"wave-started".equals(path)) return candidate;
        String normalized = candidate.toLowerCase(Locale.ROOT);
        if (normalized.contains("{stage}")
                || normalized.contains("{aura}")
                || normalized.contains("aura")
                || candidate.contains("阶段")
                || candidate.contains("前期")
                || candidate.contains("中期")
                || candidate.contains("后期")) {
            return fallback;
        }
        return candidate;
    }

    public static String sanitizeWaveName(String value) {
        return POWER_STAGE_NAME.matcher(value == null ? "" : value).replaceAll("");
    }
}

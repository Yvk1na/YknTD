package cn.ykntd.model;

import java.util.Locale;
import java.util.Optional;

public enum ContributionType {
    DAMAGE("伤害"),
    KILL("击杀"),
    REPAIR("修复"),
    DEFENSE("承伤"),
    HEALING("治疗"),
    CONTROL("控制");

    private final String displayName;

    ContributionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ContributionType> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

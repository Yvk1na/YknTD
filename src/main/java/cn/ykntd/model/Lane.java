package cn.ykntd.model;

import java.util.Locale;
import java.util.Optional;

public enum Lane {
    NORTH("北路", 0, -1),
    EAST("东路", 1, 0),
    SOUTH("南路", 0, 1),
    WEST("西路", -1, 0);

    private final String displayName;
    private final int xDirection;
    private final int zDirection;

    Lane(String displayName, int xDirection, int zDirection) {
        this.displayName = displayName;
        this.xDirection = xDirection;
        this.zDirection = zDirection;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int xDirection() {
        return xDirection;
    }

    public int zDirection() {
        return zDirection;
    }

    public static Optional<Lane> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

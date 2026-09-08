package cn.ykntd.model;

import java.util.Locale;

public enum PowerStage {
    EARLY("前期"),
    MID("中期"),
    LATE("后期");

    private final String displayName;

    PowerStage(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static PowerStage parse(String value, int waveNumber) {
        if (value == null || value.isBlank()) return forWave(waveNumber);
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知成长阶段：" + value, exception);
        }
    }

    public static PowerStage forWave(int waveNumber) {
        if (waveNumber <= 3) return EARLY;
        if (waveNumber <= 7) return MID;
        return LATE;
    }
}

package cn.ykntd.model;

import java.util.Locale;

public enum MobProvider {
    VANILLA,
    MYTHIC;

    public static MobProvider parse(String value) {
        if (value == null || value.isBlank()) return VANILLA;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知怪物提供器：" + value);
        }
    }
}

package cn.ykntd.hud;

import cn.ykntd.model.Lane;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RaidSidebarSnapshot(
        String state,
        int wave,
        int totalWaves,
        int enemies,
        int players,
        int maxPlayers,
        double coreHealth,
        double coreMaxHealth,
        double wardMaxHealth,
        Map<Lane, Double> wardHealth,
        Set<Lane> breached
) {
    private static final int BAR_SEGMENTS = 5;

    public RaidSidebarSnapshot {
        state = state == null || state.isBlank() ? "等待中" : state;
        wave = Math.max(0, wave);
        totalWaves = Math.max(1, totalWaves);
        enemies = Math.max(0, enemies);
        players = Math.max(0, players);
        maxPlayers = Math.max(1, maxPlayers);
        coreMaxHealth = positive(coreMaxHealth);
        wardMaxHealth = positive(wardMaxHealth);
        coreHealth = clampHealth(coreHealth, coreMaxHealth);

        EnumMap<Lane, Double> healthCopy = new EnumMap<>(Lane.class);
        for (Lane lane : Lane.values()) {
            double value = wardHealth == null ? wardMaxHealth
                    : wardHealth.getOrDefault(lane, wardMaxHealth);
            healthCopy.put(lane, clampHealth(value, wardMaxHealth));
        }
        wardHealth = Collections.unmodifiableMap(healthCopy);

        EnumSet<Lane> breachedCopy = EnumSet.noneOf(Lane.class);
        if (breached != null) breachedCopy.addAll(breached);
        breached = Collections.unmodifiableSet(breachedCopy);
    }

    public Map<String, String> placeholders() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("state", state);
        values.put("wave", wave == 0 ? "-" : Integer.toString(wave));
        values.put("total-waves", Integer.toString(totalWaves));
        values.put("enemies", Integer.toString(enemies));
        values.put("players", Integer.toString(players));
        values.put("max-players", Integer.toString(maxPlayers));
        values.put("ward-max", number(wardMaxHealth));
        values.put("core-max", number(coreMaxHealth));
        for (Lane lane : Lane.values()) {
            double health = wardHealth.get(lane);
            values.put(lane.key() + "-health", number(health));
            values.put(lane.key() + "-max", number(wardMaxHealth));
            values.put(lane.key(), healthStatus(
                    health, wardMaxHealth, breached.contains(lane), "已失守"));
        }
        values.put("core-health", number(coreHealth));
        values.put("core", healthStatus(coreHealth, coreMaxHealth, coreHealth <= 0.0, "已摧毁"));
        return Map.copyOf(values);
    }

    static String healthStatus(double current, double maximum, boolean destroyed, String destroyedText) {
        if (destroyed || current <= 0.0) return "&4✖ " + destroyedText;
        double ratio = Math.clamp(current / positive(maximum), 0.0, 1.0);
        int filled = Math.max(1, (int) Math.ceil(ratio * BAR_SEGMENTS));
        String color = ratio > 0.5 ? "&a" : ratio > 0.25 ? "&e" : "&c";
        return color + "▰".repeat(filled)
                + "&8" + "▱".repeat(BAR_SEGMENTS - filled)
                + " &f" + number(current);
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    private static double clampHealth(double value, double maximum) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.clamp(value, 0.0, maximum);
    }

    private static String number(double value) {
        return new DecimalFormat("0.##").format(value);
    }
}

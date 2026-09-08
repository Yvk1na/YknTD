package cn.ykntd.config;

import cn.ykntd.model.ContributionType;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

public record GameSettings(
        int minPlayers,
        int maxPlayers,
        int countdownTicks,
        int intermissionTicks,
        int tickPeriodTicks,
        int defaultWaveTimeLimitTicks,
        int mobCap,
        boolean autoStart,
        boolean teleportOnJoin,
        boolean restoreLocation,
        boolean allowLateJoin,
        boolean friendlyFire,
        boolean protectArena,
        double boundaryRadius,
        int disconnectGraceTicks,
        int allOfflineFailTicks,
        double wardMaxHealth,
        double coreMaxHealth,
        Material repairMaterial,
        double repairAmount,
        double repairRange,
        int repairCooldownTicks,
        double wardAttackRange,
        double coreAttackRange,
        int enemyAttackIntervalTicks,
        int retargetIntervalTicks,
        double playerAggroRange,
        double pathSpeed,
        boolean removeOnCoreHit,
        ScalingPolicy scaling,
        Map<ContributionType, Double> contributionWeights,
        double minimumRewardScore
) {
    public static GameSettings load(FileConfiguration config) {
        int minPlayers = positive(config.getInt("game.min-players", 1), 1);
        int maxPlayers = Math.max(minPlayers, positive(config.getInt("game.max-players", 6), 6));
        int countdownTicks = seconds(config.getDouble("game.countdown-seconds", 15.0));
        int intermissionTicks = seconds(config.getDouble("game.intermission-seconds", 12.0));
        int tickPeriod = Math.max(1, config.getInt("game.tick-period-ticks", 5));
        int waveLimit = seconds(config.getDouble("game.wave-time-limit-seconds", 240.0));
        int mobCap = positive(config.getInt("game.mob-cap", 80), 80);

        Material repairMaterial = Material.matchMaterial(
                config.getString("objectives.ward.repair-material", "AMETHYST_SHARD"));
        if (repairMaterial == null || repairMaterial.isAir()) repairMaterial = Material.AMETHYST_SHARD;

        EnumMap<ContributionType, Double> weights = new EnumMap<>(ContributionType.class);
        for (ContributionType type : ContributionType.values()) {
            weights.put(type, nonNegative(config.getDouble(
                    "contribution.weights." + type.key(), defaultWeight(type))));
        }

        ScalingPolicy scaling = new ScalingPolicy(
                config.getDouble("scaling.count-per-extra-player", 0.55),
                config.getDouble("scaling.health-per-extra-player", 0.30),
                config.getDouble("scaling.damage-per-extra-player", 0.15));

        return new GameSettings(
                minPlayers,
                maxPlayers,
                Math.max(20, countdownTicks),
                Math.max(20, intermissionTicks),
                tickPeriod,
                Math.max(20, waveLimit),
                mobCap,
                config.getBoolean("game.auto-start", true),
                config.getBoolean("game.teleport-on-join", true),
                config.getBoolean("game.restore-location", true),
                config.getBoolean("game.allow-late-join", false),
                config.getBoolean("game.friendly-fire", false),
                config.getBoolean("game.protect-arena", true),
                positive(config.getDouble("game.boundary-radius", 96.0), 96.0),
                Math.max(20, seconds(config.getDouble("game.disconnect-grace-seconds", 60.0))),
                Math.max(20, seconds(config.getDouble("game.all-offline-fail-seconds", 30.0))),
                positive(config.getDouble("objectives.ward.max-health", 100.0), 100.0),
                positive(config.getDouble("objectives.core.max-health", 180.0), 180.0),
                repairMaterial,
                positive(config.getDouble("objectives.ward.repair-amount", 12.0), 12.0),
                positive(config.getDouble("objectives.ward.repair-range", 3.0), 3.0),
                Math.max(1, config.getInt("objectives.ward.repair-cooldown-ticks", 20)),
                positive(config.getDouble("enemy.ward-attack-range", 2.4), 2.4),
                positive(config.getDouble("enemy.core-attack-range", 2.8), 2.8),
                Math.max(1, config.getInt("enemy.attack-interval-ticks", 30)),
                Math.max(1, config.getInt("enemy.retarget-interval-ticks", 20)),
                positive(config.getDouble("enemy.player-aggro-range", 12.0), 12.0),
                positive(config.getDouble("enemy.path-speed", 1.15), 1.15),
                config.getBoolean("enemy.remove-on-core-hit", true),
                scaling,
                Map.copyOf(weights),
                nonNegative(config.getDouble("rewards.minimum-score", 100.0)));
    }

    private static int seconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) return 20;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(seconds * 20.0));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double defaultWeight(ContributionType type) {
        return switch (type) {
            case DAMAGE -> 1.0;
            case KILL -> 25.0;
            case REPAIR -> 2.0;
            case DEFENSE -> 0.5;
            case HEALING -> 1.5;
            case CONTROL -> 8.0;
        };
    }
}

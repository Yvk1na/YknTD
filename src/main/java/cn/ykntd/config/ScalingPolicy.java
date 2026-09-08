package cn.ykntd.config;

public record ScalingPolicy(
        double countPerExtraPlayer,
        double healthPerExtraPlayer,
        double damagePerExtraPlayer
) {
    public ScalingPolicy {
        countPerExtraPlayer = sanitize(countPerExtraPlayer);
        healthPerExtraPlayer = sanitize(healthPerExtraPlayer);
        damagePerExtraPlayer = sanitize(damagePerExtraPlayer);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    public double countMultiplier(int players) {
        return 1.0 + Math.max(0, players - 1) * countPerExtraPlayer;
    }

    public double healthMultiplier(int players) {
        return 1.0 + Math.max(0, players - 1) * healthPerExtraPlayer;
    }

    public double damageMultiplier(int players) {
        return 1.0 + Math.max(0, players - 1) * damagePerExtraPlayer;
    }

    public int scaleCount(int baseAmount, int players) {
        if (baseAmount <= 0) return 0;
        return Math.max(1, (int) Math.ceil(baseAmount * countMultiplier(players)));
    }

    public int scaleCountByLane(int baseAmount, int players, int lanes) {
        if (baseAmount <= 0) return 0;
        if (lanes <= 1 || baseAmount % lanes != 0) return scaleCount(baseAmount, players);
        int perLane = baseAmount / lanes;
        return scaleCount(perLane, players) * lanes;
    }
}

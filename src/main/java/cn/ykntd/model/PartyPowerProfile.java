package cn.ykntd.model;

public record PartyPowerProfile(boolean available, double offenseIndex,
                                double defenseIndex, int sampledPlayers) {
    private static final double HEALTH_EXPONENT = 0.65;
    private static final double DAMAGE_EXPONENT = 0.50;
    private static final double MIN_HEALTH_MULTIPLIER = 0.80;
    private static final double MAX_HEALTH_MULTIPLIER = 1.25;
    private static final double MIN_DAMAGE_MULTIPLIER = 0.85;
    private static final double MAX_DAMAGE_MULTIPLIER = 1.20;

    public PartyPowerProfile {
        if (!available || !Double.isFinite(offenseIndex)
                || !Double.isFinite(defenseIndex) || sampledPlayers < 1) {
            available = false;
            offenseIndex = 0.0;
            defenseIndex = 0.0;
            sampledPlayers = 0;
        } else {
            offenseIndex = clamp(offenseIndex, 0.0, AuraPowerIndex.MAX_INDEX);
            defenseIndex = clamp(defenseIndex, 0.0, AuraPowerIndex.MAX_INDEX);
        }
    }

    public PartyPowerProfile(boolean available, double auraIndex, int sampledPlayers) {
        this(available, auraIndex, auraIndex, sampledPlayers);
    }

    public static PartyPowerProfile unavailable() {
        return new PartyPowerProfile(false, 0.0, 0.0, 0);
    }

    public double healthMultiplier(int referenceAuraLevel) {
        if (!available) return 1.0;
        double reference = AuraPowerIndex.referenceOffenseFactor(referenceLevel(referenceAuraLevel));
        double actual = AuraPowerIndex.referenceOffenseFactor(offenseIndex);
        return clamp(Math.pow(actual / reference, HEALTH_EXPONENT),
                MIN_HEALTH_MULTIPLIER, MAX_HEALTH_MULTIPLIER);
    }

    public double damageMultiplier(int referenceAuraLevel) {
        if (!available) return 1.0;
        double reference = AuraPowerIndex.referenceEhpFactor(referenceLevel(referenceAuraLevel));
        double actual = AuraPowerIndex.referenceEhpFactor(defenseIndex);
        return clamp(Math.pow(actual / reference, DAMAGE_EXPONENT),
                MIN_DAMAGE_MULTIPLIER, MAX_DAMAGE_MULTIPLIER);
    }

    public double auraIndex() {
        return (offenseIndex + defenseIndex) / 2.0;
    }

    public String displayValue() {
        return available ? Long.toString(Math.round(auraIndex())) : "未接入";
    }

    private int referenceLevel(int referenceAuraLevel) {
        return Math.max(0, Math.min(100, referenceAuraLevel));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

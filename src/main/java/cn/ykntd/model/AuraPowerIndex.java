package cn.ykntd.model;

/**
 * Converts AuraSkills' effective combat traits back to the 0-100 progression
 * scale used by the bundled wave definitions.
 *
 * <p>The reference curve models the newly generated 53-pattern reward set with
 * all enabled skills at the same level. Rewards grow at 1.0x through level 50,
 * 1.25x through level 80, and 1.875x through level 100. Values above the normal
 * cap are kept up to 150 so pets and other non-equipment Aura modifiers can
 * still influence difficulty.</p>
 */
public final class AuraPowerIndex {
    public static final double MAX_INDEX = 150.0;

    private static final double BASE_HEALTH = 20.0;
    private static final double REFERENCE_ATTACK_BONUS_PER_PROGRESS = 0.08;
    private static final double REFERENCE_HEALTH_PER_PROGRESS = 0.32;
    private static final double REFERENCE_REDUCTION_INPUT_PER_PROGRESS = 0.144;
    private static final double REFERENCE_REGEN_PER_PROGRESS = 0.0032;
    private static final double REFERENCE_CRIT_CHANCE_PER_PROGRESS = 0.12;
    private static final double REFERENCE_CRIT_DAMAGE_PER_PROGRESS = 0.20;
    private static final double EHP_WEIGHT = 0.85;
    private static final double REGEN_WEIGHT = 1.0 - EHP_WEIGHT;

    private AuraPowerIndex() {
    }

    public static double offenseIndex(double attackBonusPercent,
                                      double criticalChancePercent,
                                      double criticalDamagePercent) {
        double attack = nonNegative(attackBonusPercent);
        double chance = clamp(nonNegative(criticalChancePercent), 0.0, 100.0);
        double criticalDamage = nonNegative(criticalDamagePercent);
        double targetFactor = (1.0 + attack / 100.0)
                * (1.0 + chance / 100.0 * criticalDamage / 100.0);
        return invert(targetFactor, AuraPowerIndex::referenceOffenseFactor);
    }

    public static double defenseIndex(double bonusHealth,
                                      double damageReductionInput,
                                      double regenerationTrait) {
        double health = Math.max(BASE_HEALTH, BASE_HEALTH + finiteOrZero(bonusHealth));
        double reductionInput = nonNegative(damageReductionInput);
        // AuraSkills uses 1 - 1.01^(-input), so EHP is multiplied by 1.01^input.
        double targetEhpFactor = health / BASE_HEALTH * Math.pow(1.01, reductionInput);
        double ehpIndex = invert(targetEhpFactor, AuraPowerIndex::referenceEhpFactor);
        double regenIndex = invert(nonNegative(regenerationTrait),
                level -> REFERENCE_REGEN_PER_PROGRESS * referenceProgress(level));
        return clamp(ehpIndex * EHP_WEIGHT + regenIndex * REGEN_WEIGHT, 0.0, MAX_INDEX);
    }

    public static double referenceOffenseFactor(double level) {
        double progress = referenceProgress(level);
        double attackBonus = REFERENCE_ATTACK_BONUS_PER_PROGRESS * progress;
        double criticalChance = REFERENCE_CRIT_CHANCE_PER_PROGRESS * progress;
        double criticalDamage = REFERENCE_CRIT_DAMAGE_PER_PROGRESS * progress;
        return (1.0 + attackBonus / 100.0)
                * (1.0 + criticalChance / 100.0 * criticalDamage / 100.0);
    }

    public static double referenceEhpFactor(double level) {
        double progress = referenceProgress(level);
        return (BASE_HEALTH + REFERENCE_HEALTH_PER_PROGRESS * progress) / BASE_HEALTH
                * Math.pow(1.01, REFERENCE_REDUCTION_INPUT_PER_PROGRESS * progress);
    }

    public static double referenceProgress(double level) {
        double bounded = clamp(finiteOrZero(level), 0.0, MAX_INDEX);
        if (bounded <= 50.0) return bounded;
        if (bounded <= 80.0) return 50.0 + (bounded - 50.0) * 1.25;
        return 87.5 + (bounded - 80.0) * 1.875;
    }

    private static double invert(double target, Curve curve) {
        if (!Double.isFinite(target) || target <= curve.value(0.0)) return 0.0;
        if (target >= curve.value(MAX_INDEX)) return MAX_INDEX;
        double low = 0.0;
        double high = MAX_INDEX;
        for (int iteration = 0; iteration < 60; iteration++) {
            double middle = (low + high) / 2.0;
            if (curve.value(middle) < target) low = middle;
            else high = middle;
        }
        return (low + high) / 2.0;
    }

    private static double nonNegative(double value) {
        return Math.max(0.0, finiteOrZero(value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @FunctionalInterface
    private interface Curve {
        double value(double level);
    }
}

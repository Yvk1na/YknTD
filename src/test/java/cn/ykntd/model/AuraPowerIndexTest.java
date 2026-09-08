package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraPowerIndexTest {
    private static final double EPSILON = 1.0e-7;

    @Test
    void regeneratedRewardCurveMapsUniformSkillLevelsBackToTheirLevel() {
        for (double level : new double[]{0, 10, 20, 30, 40, 50, 60, 70, 85, 100}) {
            double progress = AuraPowerIndex.referenceProgress(level);
            double attackBonus = 0.08 * progress;
            double healthBonus = 0.32 * progress;
            double reductionInput = 0.144 * progress;
            double regeneration = 0.0032 * progress;
            double criticalChance = 0.12 * progress;
            double criticalDamage = 0.20 * progress;

            assertAll("Aura reference level " + level,
                    () -> assertEquals(level, AuraPowerIndex.offenseIndex(
                            attackBonus, criticalChance, criticalDamage), EPSILON),
                    () -> assertEquals(level, AuraPowerIndex.defenseIndex(
                            healthBonus, reductionInput, regeneration), EPSILON));
        }
    }

    @Test
    void externalModifiersRemainFiniteAndAreCappedAboveTheNormalLevelRange() {
        double offense = AuraPowerIndex.offenseIndex(500.0, 100.0, 500.0);
        double defense = AuraPowerIndex.defenseIndex(500.0, 500.0, 20.0);

        assertAll(
                () -> assertEquals(AuraPowerIndex.MAX_INDEX, offense, EPSILON),
                () -> assertEquals(AuraPowerIndex.MAX_INDEX, defense, EPSILON),
                () -> assertEquals(0.0,
                        AuraPowerIndex.offenseIndex(Double.NaN, Double.NaN, Double.NaN), EPSILON),
                () -> assertEquals(0.0,
                        AuraPowerIndex.defenseIndex(Double.NaN, Double.NaN, Double.NaN), EPSILON),
                () -> assertTrue(AuraPowerIndex.referenceEhpFactor(100)
                        > AuraPowerIndex.referenceEhpFactor(50))
        );
    }
}

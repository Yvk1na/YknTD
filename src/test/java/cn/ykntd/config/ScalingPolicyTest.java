package cn.ykntd.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScalingPolicyTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void constructorSanitizesNegativeAndNonFiniteRates() {
        ScalingPolicy policy = new ScalingPolicy(-0.25, Double.NaN, Double.POSITIVE_INFINITY);
        ScalingPolicy negativeInfinity = new ScalingPolicy(0.5, Double.NEGATIVE_INFINITY, 0.75);

        assertAll(
                () -> assertEquals(0.0, policy.countPerExtraPlayer(), EPSILON),
                () -> assertEquals(0.0, policy.healthPerExtraPlayer(), EPSILON),
                () -> assertEquals(0.0, policy.damagePerExtraPlayer(), EPSILON),
                () -> assertEquals(0.0, negativeInfinity.healthPerExtraPlayer(), EPSILON)
        );
    }

    @Test
    void oneOrFewerPlayersAlwaysUsesBaseMultipliers() {
        ScalingPolicy policy = new ScalingPolicy(0.5, 0.25, 0.1);

        for (int players : new int[]{-1, 0, 1}) {
            assertAll(
                    () -> assertEquals(1.0, policy.countMultiplier(players), EPSILON),
                    () -> assertEquals(1.0, policy.healthMultiplier(players), EPSILON),
                    () -> assertEquals(1.0, policy.damageMultiplier(players), EPSILON)
            );
        }
    }

    @Test
    void multipliersScaleOnlyForExtraPlayers() {
        ScalingPolicy policy = new ScalingPolicy(0.5, 0.25, 0.1);

        assertAll(
                () -> assertEquals(2.5, policy.countMultiplier(4), EPSILON),
                () -> assertEquals(1.75, policy.healthMultiplier(4), EPSILON),
                () -> assertEquals(1.3, policy.damageMultiplier(4), EPSILON)
        );
    }

    @Test
    void scaleCountRejectsNonPositiveBaseAndRoundsPositiveCountsUp() {
        ScalingPolicy policy = new ScalingPolicy(0.25, 0.0, 0.0);

        assertAll(
                () -> assertEquals(0, policy.scaleCount(Integer.MIN_VALUE, 4)),
                () -> assertEquals(0, policy.scaleCount(0, 4)),
                () -> assertEquals(3, policy.scaleCount(3, 1)),
                () -> assertEquals(4, policy.scaleCount(3, 2)),
                () -> assertEquals(5, policy.scaleCount(3, 3))
        );
    }

    @Test
    void perLaneScalingKeepsEvenRoutePressure() {
        ScalingPolicy policy = new ScalingPolicy(0.22, 0.0, 0.0);

        assertAll(
                () -> assertEquals(16, policy.scaleCountByLane(12, 2, 4)),
                () -> assertEquals(24, policy.scaleCountByLane(12, 5, 4)),
                () -> assertEquals(4, policy.scaleCountByLane(2, 2, 2)),
                () -> assertEquals(4, policy.scaleCountByLane(3, 2, 4)),
                () -> assertEquals(0, policy.scaleCountByLane(0, 6, 4))
        );
    }
}

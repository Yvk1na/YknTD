package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContributionTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void startsWithEveryContributionTypeAtZero() {
        Contribution contribution = new Contribution();
        Map<ContributionType, Double> snapshot = contribution.snapshot();

        assertEquals(ContributionType.values().length, snapshot.size());
        for (ContributionType type : ContributionType.values()) {
            assertEquals(0.0, contribution.value(type), EPSILON);
        }
        assertEquals(0.0, contribution.value(null), EPSILON);
    }

    @Test
    void addAccumulatesPositiveFiniteAmountsAndIgnoresInvalidInput() {
        Contribution contribution = new Contribution();

        contribution.add(ContributionType.DAMAGE, 10.0);
        contribution.add(ContributionType.DAMAGE, 2.5);
        contribution.add(null, 100.0);
        contribution.add(ContributionType.DAMAGE, 0.0);
        contribution.add(ContributionType.DAMAGE, -1.0);
        contribution.add(ContributionType.DAMAGE, Double.NaN);
        contribution.add(ContributionType.DAMAGE, Double.POSITIVE_INFINITY);
        contribution.add(ContributionType.DAMAGE, Double.NEGATIVE_INFINITY);

        assertEquals(12.5, contribution.value(ContributionType.DAMAGE), EPSILON);
    }

    @Test
    void scoreUsesOnlyPositiveFiniteWeights() {
        Contribution contribution = new Contribution();
        contribution.add(ContributionType.DAMAGE, 10.0);
        contribution.add(ContributionType.REPAIR, 4.0);
        contribution.add(ContributionType.HEALING, 100.0);

        EnumMap<ContributionType, Double> weights = new EnumMap<>(ContributionType.class);
        weights.put(ContributionType.DAMAGE, 1.5);
        weights.put(ContributionType.REPAIR, 2.0);
        weights.put(ContributionType.HEALING, -3.0);
        weights.put(ContributionType.KILL, Double.NaN);
        weights.put(ContributionType.DEFENSE, Double.POSITIVE_INFINITY);

        assertEquals(23.0, contribution.score(weights), EPSILON);
        assertEquals(0.0, contribution.score(Map.of()), EPSILON);
    }

    @Test
    void snapshotIsUnmodifiableAndDetachedFromLaterChanges() {
        Contribution contribution = new Contribution();
        contribution.add(ContributionType.REPAIR, 2.0);
        Map<ContributionType, Double> snapshot = contribution.snapshot();

        contribution.add(ContributionType.REPAIR, 3.0);

        assertAll(
                () -> assertEquals(2.0, snapshot.get(ContributionType.REPAIR), EPSILON),
                () -> assertEquals(5.0, contribution.snapshot().get(ContributionType.REPAIR), EPSILON),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.put(ContributionType.REPAIR, 99.0)
                )
        );
    }
}

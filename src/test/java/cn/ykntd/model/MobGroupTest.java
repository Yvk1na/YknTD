package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MobGroupTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void rejectsMissingIdNonPositiveAmountAndMissingLanes() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> group(null, 1, List.of(Lane.NORTH))),
                () -> assertThrows(IllegalArgumentException.class, () -> group("   ", 1, List.of(Lane.NORTH))),
                () -> assertThrows(IllegalArgumentException.class, () -> group("zombie", 0, List.of(Lane.NORTH))),
                () -> assertThrows(IllegalArgumentException.class, () -> group("zombie", -1, List.of(Lane.NORTH))),
                () -> assertThrows(IllegalArgumentException.class, () -> group("zombie", 1, List.of())),
                () -> assertThrows(NullPointerException.class, () -> group("zombie", 1, null)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> group("zombie", 1, Arrays.asList(Lane.NORTH, null))
                )
        );
    }

    @Test
    void normalizesInvalidNumericValuesToOne() {
        MobGroup group = new MobGroup(
                MobProvider.VANILLA,
                "zombie",
                1,
                List.of(Lane.NORTH),
                Double.NaN,
                Double.NaN,
                -1.0,
                0.0,
                -1.0,
                Double.POSITIVE_INFINITY,
                false
        );

        assertAll(
                () -> assertEquals(1.0, group.level(), EPSILON),
                () -> assertEquals(0.0, group.baseHealth(), EPSILON),
                () -> assertEquals(0.0, group.baseDamage(), EPSILON),
                () -> assertEquals(1.0, group.healthMultiplier(), EPSILON),
                () -> assertEquals(1.0, group.damageMultiplier(), EPSILON),
                () -> assertEquals(1.0, group.wardDamage(), EPSILON)
        );
    }

    @Test
    void preservesPositiveFiniteNumericValuesIncludingSmallFractions() {
        MobGroup group = new MobGroup(
                MobProvider.MYTHIC,
                "raider",
                1,
                List.of(Lane.EAST),
                Double.MIN_VALUE,
                250.0,
                75.0,
                0.25,
                2.5,
                3.0,
                true
        );

        assertAll(
                () -> assertEquals(Double.MIN_VALUE, group.level()),
                () -> assertEquals(250.0, group.baseHealth(), EPSILON),
                () -> assertEquals(75.0, group.baseDamage(), EPSILON),
                () -> assertEquals(0.25, group.healthMultiplier(), EPSILON),
                () -> assertEquals(2.5, group.damageMultiplier(), EPSILON),
                () -> assertEquals(3.0, group.wardDamage(), EPSILON)
        );
    }

    @Test
    void copiesLaneListAndExposesItAsUnmodifiable() {
        List<Lane> lanes = new ArrayList<>(List.of(Lane.NORTH, Lane.WEST));
        MobGroup group = group("zombie", 1, lanes);

        lanes.clear();

        assertAll(
                () -> assertEquals(List.of(Lane.NORTH, Lane.WEST), group.lanes()),
                () -> assertThrows(UnsupportedOperationException.class, () -> group.lanes().add(Lane.EAST))
        );
    }

    private static MobGroup group(String id, int amount, List<Lane> lanes) {
        return new MobGroup(MobProvider.VANILLA, id, amount, lanes,
                1.0, 0.0, 0.0, 1.0, 1.0, 1.0, false);
    }
}

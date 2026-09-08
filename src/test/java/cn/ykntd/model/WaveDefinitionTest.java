package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveDefinitionTest {
    @Test
    void rejectsNonPositiveNumberAndMissingGroups() {
        MobGroup group = validGroup();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> wave(0, "波次", 1, 20, List.of(group))),
                () -> assertThrows(IllegalArgumentException.class, () -> wave(-1, "波次", 1, 20, List.of(group))),
                () -> assertThrows(IllegalArgumentException.class, () -> wave(1, "波次", 1, 20, List.of())),
                () -> assertThrows(NullPointerException.class, () -> wave(1, "波次", 1, 20, null)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> wave(1, "波次", 1, 20, Arrays.asList(group, null))
                )
        );
    }

    @Test
    void defaultsMissingNameAndClampsTickBounds() {
        MobGroup group = validGroup();
        WaveDefinition nullName = wave(3, null, Integer.MIN_VALUE, Integer.MIN_VALUE, List.of(group));
        WaveDefinition blankName = wave(4, "  ", 0, 19, List.of(group));

        assertAll(
                () -> assertEquals("第 3 波", nullName.name()),
                () -> assertEquals(1, nullName.spawnIntervalTicks()),
                () -> assertEquals(20, nullName.timeLimitTicks()),
                () -> assertEquals("第 4 波", blankName.name()),
                () -> assertEquals(1, blankName.spawnIntervalTicks()),
                () -> assertEquals(20, blankName.timeLimitTicks())
        );
    }

    @Test
    void defaultsStageAndClampsAuraReference() {
        MobGroup group = validGroup();
        WaveDefinition early = new WaveDefinition(
                2, "前期", null, -10, 1, 20, List.of(group));
        WaveDefinition late = new WaveDefinition(
                9, "后期", null, 500, 1, 20, List.of(group));

        assertAll(
                () -> assertEquals(PowerStage.EARLY, early.stage()),
                () -> assertEquals(0, early.recommendedAuraLevel()),
                () -> assertEquals(PowerStage.LATE, late.stage()),
                () -> assertEquals(100, late.recommendedAuraLevel())
        );
    }

    @Test
    void preservesValuesAtAndAboveTickBounds() {
        WaveDefinition wave = wave(1, "夜袭", 1, 20, List.of(validGroup()));

        assertAll(
                () -> assertEquals("夜袭", wave.name()),
                () -> assertEquals(1, wave.spawnIntervalTicks()),
                () -> assertEquals(20, wave.timeLimitTicks())
        );
    }

    @Test
    void copiesGroupListAndExposesItAsUnmodifiable() {
        MobGroup group = validGroup();
        List<MobGroup> groups = new ArrayList<>(List.of(group));
        WaveDefinition wave = wave(1, "夜袭", 20, 200, groups);

        groups.clear();

        assertAll(
                () -> assertEquals(List.of(group), wave.groups()),
                () -> assertThrows(UnsupportedOperationException.class, () -> wave.groups().add(group))
        );
    }

    private static WaveDefinition wave(
            int number,
            String name,
            int spawnIntervalTicks,
            int timeLimitTicks,
            List<MobGroup> groups
    ) {
        return new WaveDefinition(number, name, PowerStage.forWave(number),
                number * 10, spawnIntervalTicks, timeLimitTicks, groups);
    }

    private static MobGroup validGroup() {
        return new MobGroup(
                MobProvider.VANILLA,
                "zombie",
                1,
                List.of(Lane.NORTH),
                1.0,
                0.0,
                0.0,
                1.0,
                1.0,
                1.0,
                false
        );
    }
}

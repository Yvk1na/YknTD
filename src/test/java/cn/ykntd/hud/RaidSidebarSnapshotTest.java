package cn.ykntd.hud;

import cn.ykntd.model.Lane;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidSidebarSnapshotTest {
    @Test
    void rendersFourLanesEnemyCountAndCoreProgress() {
        EnumMap<Lane, Double> health = new EnumMap<>(Lane.class);
        health.put(Lane.NORTH, 500.0);
        health.put(Lane.EAST, 375.0);
        health.put(Lane.SOUTH, 125.0);
        health.put(Lane.WEST, 0.0);

        Map<String, String> values = new RaidSidebarSnapshot(
                "防守中", 4, 10, 17, 3, 6,
                7_500.0, 10_000.0, 500.0,
                health, EnumSet.of(Lane.WEST)).placeholders();

        assertEquals("防守中", values.get("state"));
        assertEquals("4", values.get("wave"));
        assertEquals("10", values.get("total-waves"));
        assertEquals("17", values.get("enemies"));
        assertEquals("500", values.get("north-max"));
        assertTrue(values.get("north").contains("▰▰▰▰▰"));
        assertTrue(values.get("east").startsWith("&a"));
        assertTrue(values.get("south").startsWith("&c"));
        assertEquals("&4✖ 已失守", values.get("west"));
        assertTrue(values.get("core").contains("7500"));
    }

    @Test
    void waitingWaveUsesDashAndDestroyedCoreIsExplicit() {
        Map<String, String> values = new RaidSidebarSnapshot(
                "等待中", 0, 10, 0, 1, 6,
                0.0, 10_000.0, 500.0,
                Map.of(), EnumSet.noneOf(Lane.class)).placeholders();

        assertEquals("-", values.get("wave"));
        assertEquals("&4✖ 已摧毁", values.get("core"));
    }
}

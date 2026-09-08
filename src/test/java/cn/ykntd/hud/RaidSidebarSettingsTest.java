package cn.ykntd.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidSidebarSettingsTest {
    @Test
    void fallsBackToPolishedDefaultLayout() {
        RaidSidebarSettings settings = new RaidSidebarSettings(true, null, List.of());

        assertTrue(settings.enabled());
        assertEquals("&b&lSKYBLOCK", settings.title());
        assertTrue(settings.lines().stream().anyMatch(line -> line.contains("{north}")));
        assertTrue(settings.lines().stream().anyMatch(line -> line.contains("{enemies}")));
        assertTrue(settings.lines().size() <= 15);
    }

    @Test
    void limitsCustomLayoutToMinecraftMaximum() {
        RaidSidebarSettings settings = new RaidSidebarSettings(false, "&c测试",
                new ArrayList<>(List.of(
                "1", "2", "3", "4", "5", "6", "7", "8",
                "9", "10", "11", "12", "13", "14", "15", "16")));

        assertEquals(false, settings.enabled());
        assertEquals("&c测试", settings.title());
        assertEquals(15, settings.lines().size());
        assertEquals("15", settings.lines().getLast());
    }

    @Test
    void migratesLegacyRaidTitleWithoutTouchingSkyblockCore() {
        RaidSidebarSettings settings = new RaidSidebarSettings(true, "&5&l浮岛夜袭", List.of("状态"));

        assertEquals("&b&lSKYBLOCK", settings.title());
    }
}

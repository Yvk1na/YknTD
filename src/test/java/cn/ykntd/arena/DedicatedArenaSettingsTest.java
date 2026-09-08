package cn.ykntd.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedArenaSettingsTest {
    private final DedicatedArenaSettings base = settings("night_raid", "floating_island_night_raid",
            96, 80, 731946285104L);

    @Test
    void generationFingerprintTracksThePremiumArenaRevision() {
        assertEquals("3:80:731946285104", base.generationFingerprint());
    }

    @Test
    void borderRadiusCanHotReloadWithoutChangingGeneratedWorldIdentity() {
        DedicatedArenaSettings widerBorder = settings("night_raid", "floating_island_night_raid",
                128, 80, 731946285104L);

        assertTrue(base.sameGeneratedWorld(widerBorder));
        assertTrue(base.generationFingerprint().equals(widerBorder.generationFingerprint()));
    }

    @Test
    void resetPolicyCanHotReloadWithoutChangingGeneratedWorldIdentity() {
        DedicatedArenaSettings noAutomaticReset = new DedicatedArenaSettings(
                true, "night_raid", "floating_island_night_raid", 10, 96, 80,
                731946285104L, true, false);

        assertTrue(base.sameGeneratedWorld(noAutomaticReset));
        assertTrue(base.generationFingerprint().equals(noAutomaticReset.generationFingerprint()));

        DedicatedArenaSettings lazyAndDisabled = new DedicatedArenaSettings(
                false, "night_raid", "floating_island_night_raid", 10, 96, 80,
                731946285104L, false, true);
        assertTrue(base.sameGeneratedWorld(lazyAndDisabled));
    }

    @Test
    void layoutOrWorldIdentityChangesRequireRestart() {
        assertFalse(base.sameGeneratedWorld(settings("another", "floating_island_night_raid",
                96, 80, 731946285104L)));
        assertFalse(base.sameGeneratedWorld(settings("night_raid", "another_world",
                96, 80, 731946285104L)));
        DedicatedArenaSettings fewerInstances = new DedicatedArenaSettings(
                true, "night_raid", "floating_island_night_raid", 5, 96, 80,
                731946285104L, true, true);
        assertFalse(base.sameGeneratedWorld(fewerInstances));
        assertFalse(base.sameGeneratedWorld(settings("night_raid", "floating_island_night_raid",
                96, 90, 731946285104L)));
        DedicatedArenaSettings differentSeed = settings("night_raid", "floating_island_night_raid",
                96, 80, 42L);
        assertFalse(base.sameGeneratedWorld(differentSeed));
        assertNotEquals(base.generationFingerprint(), differentSeed.generationFingerprint());
    }

    @Test
    void instanceNamesAndIdsAreStableAndReversible() {
        assertEquals("tdmap/floating_island_night_raid_01", base.managedWorldName(1));
        assertEquals("tdmap/floating_island_night_raid_10", base.managedWorldName(10));
        assertEquals("night_raid_01", base.instanceArenaId(1));
        assertEquals("night_raid_10", base.instanceArenaId(10));
        assertEquals(7, base.instanceSlot("NIGHT_RAID_07").orElseThrow());
        assertTrue(base.isManagedWorldName("TDMAP/FLOATING_ISLAND_NIGHT_RAID_01"));
        assertTrue(base.isManagedWorldName("tdmap/floating_island_night_raid_10"));
        assertFalse(base.isManagedWorldName("tdmap/floating_island_night_raid_11"));
        assertTrue(base.instanceSlot("night_raid_1").isEmpty());
        assertTrue(base.instanceSlot("night_raid").isEmpty());
        assertTrue(base.instanceSlot("night_raid_11").isEmpty());
        assertTrue(base.isInternalInstanceId("NIGHT_RAID_01"));
        assertTrue(base.isInternalInstanceId("night_raid_10"));
        assertFalse(base.isInternalInstanceId("night_raid_1"));
        assertFalse(base.isInternalInstanceId("night_raid_11"));
    }

    @Test
    void allTenInternalIdsRemainReservedWhenPoolIsConfiguredSmaller() {
        DedicatedArenaSettings smallerPool = new DedicatedArenaSettings(
                true, "night_raid", "floating_island_night_raid", 3, 96, 80,
                731946285104L, false, true);

        assertTrue(smallerPool.instanceSlot("night_raid_10").isEmpty());
        assertTrue(smallerPool.isInternalInstanceId("night_raid_10"));
    }

    private static DedicatedArenaSettings settings(String arenaId, String worldName,
                                                    int radius, int floorY, long seed) {
        return new DedicatedArenaSettings(true, arenaId, worldName, 10, radius, floorY, seed, true, true);
    }
}

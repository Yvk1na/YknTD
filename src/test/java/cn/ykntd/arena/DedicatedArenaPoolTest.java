package cn.ykntd.arena;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedArenaPoolTest {
    @Test
    void rejectsCapacityOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new DedicatedArenaPool<>(0));
        assertThrows(IllegalArgumentException.class, () -> new DedicatedArenaPool<>(11));
    }

    @Test
    void reservesTenSlotsRejectsEleventhAndReusesReleasedSlot() {
        DedicatedArenaPool<String> pool = new DedicatedArenaPool<>(10);
        List<Integer> slots = new ArrayList<>();
        String[] owners = new String[10];
        for (int expected = 1; expected <= 10; expected++) {
            int slot = pool.reserveFirstAvailable().orElseThrow();
            slots.add(slot);
            assertEquals(expected, slot);
            owners[slot - 1] = "session-" + slot;
            assertTrue(pool.activate(slot, owners[slot - 1]));
        }

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), slots);
        assertTrue(pool.reserveFirstAvailable().isEmpty());
        assertEquals(10, pool.activeCount());
        assertTrue(pool.release(5, owners[4]));
        assertEquals(5, pool.reserveFirstAvailable().orElseThrow());
    }

    @Test
    void reservationCountsAsInUseAndOldOwnerCannotReleaseReplacement() {
        DedicatedArenaPool<String> pool = new DedicatedArenaPool<>(1);
        String first = new String("same");
        String replacement = new String("same");
        int slot = pool.reserveFirstAvailable().orElseThrow();

        assertTrue(pool.inUse(slot));
        assertTrue(pool.reserveFirstAvailable().isEmpty());
        assertTrue(pool.activate(slot, first));
        assertTrue(pool.release(slot, first));
        assertEquals(slot, pool.reserveFirstAvailable().orElseThrow());
        assertTrue(pool.activate(slot, replacement));
        assertFalse(pool.release(slot, first));
        assertEquals(replacement, pool.active(slot).orElseThrow());
    }

    @Test
    void allocationCanSkipWorldsThatAreNotSafeToReset() {
        DedicatedArenaPool<String> pool = new DedicatedArenaPool<>(3);
        int slot = pool.reserveFirstAvailable(candidate -> candidate != 1).orElseThrow();

        assertEquals(2, slot);
        pool.cancelReservation(slot);
        assertFalse(pool.inUse(slot));
    }

    @Test
    void exactReservationAllowsIndependentPartyToChooseAFreeSlot() {
        DedicatedArenaPool<String> pool = new DedicatedArenaPool<>(10);

        assertTrue(pool.reserve(7));
        assertFalse(pool.reserve(7));
        assertEquals(1, pool.reserveFirstAvailable().orElseThrow());
        pool.cancelReservation(7);
        assertTrue(pool.reserve(7));
    }
}

package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneTest {
    @Test
    void directionVectorsMatchTheArenaCompassContract() {
        assertAll(
                () -> assertEquals(0, Lane.NORTH.xDirection()),
                () -> assertEquals(-1, Lane.NORTH.zDirection()),
                () -> assertEquals(1, Lane.EAST.xDirection()),
                () -> assertEquals(0, Lane.EAST.zDirection()),
                () -> assertEquals(0, Lane.SOUTH.xDirection()),
                () -> assertEquals(1, Lane.SOUTH.zDirection()),
                () -> assertEquals(-1, Lane.WEST.xDirection()),
                () -> assertEquals(0, Lane.WEST.zDirection())
        );
    }

    @Test
    void everyDirectionIsAUniqueCardinalUnitVector() {
        Set<String> seenVectors = new HashSet<>();

        for (Lane lane : Lane.values()) {
            assertAll(lane.name(),
                    () -> assertEquals(1, Math.abs(lane.xDirection()) + Math.abs(lane.zDirection())),
                    () -> assertTrue(seenVectors.add(lane.xDirection() + ":" + lane.zDirection()))
            );
        }
    }

    @Test
    void parseAcceptsStableKeysCaseAndWhitespaceButRejectsUnknownValues() {
        for (Lane lane : Lane.values()) {
            assertAll(lane.name(),
                    () -> assertEquals(lane, Lane.parse(lane.key()).orElseThrow()),
                    () -> assertEquals(lane, Lane.parse("  " + lane.name() + "  ").orElseThrow())
            );
        }

        assertAll(
                () -> assertTrue(Lane.parse(null).isEmpty()),
                () -> assertTrue(Lane.parse("").isEmpty()),
                () -> assertEquals(Lane.SOUTH, Lane.parse("south").orElseThrow()),
                () -> assertTrue(Lane.parse("north-east").isEmpty())
        );
    }
}

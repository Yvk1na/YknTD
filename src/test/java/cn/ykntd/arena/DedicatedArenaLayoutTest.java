package cn.ykntd.arena;

import cn.ykntd.model.Lane;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedicatedArenaLayoutTest {
    private final DedicatedArenaLayout layout = new DedicatedArenaLayout(80);

    @Test
    void lanesPointToFourDistinctDirections() {
        assertAll(
                () -> assertEquals(new DedicatedArenaLayout.Coordinate(0, -70),
                        layout.coordinate(Lane.NORTH, DedicatedArenaLayout.SPAWN_DISTANCE)),
                () -> assertEquals(new DedicatedArenaLayout.Coordinate(70, 0),
                        layout.coordinate(Lane.EAST, DedicatedArenaLayout.SPAWN_DISTANCE)),
                () -> assertEquals(new DedicatedArenaLayout.Coordinate(0, 70),
                        layout.coordinate(Lane.SOUTH, DedicatedArenaLayout.SPAWN_DISTANCE)),
                () -> assertEquals(new DedicatedArenaLayout.Coordinate(-70, 0),
                        layout.coordinate(Lane.WEST, DedicatedArenaLayout.SPAWN_DISTANCE))
        );
    }

    @Test
    void lobbyIsOffsetFromEveryAttackAxis() {
        assertAll(
                () -> assertTrue(DedicatedArenaLayout.LOBBY_X != 0),
                () -> assertTrue(DedicatedArenaLayout.LOBBY_Z != 0),
                () -> assertTrue(DedicatedArenaLayout.LOBBY_X * DedicatedArenaLayout.LOBBY_X
                        + DedicatedArenaLayout.LOBBY_Z * DedicatedArenaLayout.LOBBY_Z < 24 * 24)
        );
    }

    @Test
    void allGeneratedObjectivesStayInsideDefaultBoundary() {
        for (Lane lane : Lane.values()) {
            DedicatedArenaLayout.Coordinate spawn = layout.coordinate(lane, DedicatedArenaLayout.SPAWN_DISTANCE);
            DedicatedArenaLayout.Coordinate ward = layout.coordinate(lane, DedicatedArenaLayout.WARD_DISTANCE);
            assertAll(
                    () -> assertEquals(4900, spawn.x() * spawn.x() + spawn.z() * spawn.z()),
                    () -> assertEquals(841, ward.x() * ward.x() + ward.z() * ward.z())
            );
        }
    }

    @Test
    void routeDistancesMoveStrictlyFromSpawnTowardWardAndCore() {
        List<Integer> routeDistances = new ArrayList<>();
        routeDistances.add(DedicatedArenaLayout.SPAWN_DISTANCE);
        routeDistances.addAll(DedicatedArenaLayout.WAYPOINT_DISTANCES);
        routeDistances.add(DedicatedArenaLayout.WARD_DISTANCE);
        routeDistances.add(0);

        assertEquals(List.of(70, 56, 42, 29, 0), routeDistances);
        for (Lane lane : Lane.values()) {
            int previousRadiusSquared = Integer.MAX_VALUE;
            for (int distance : routeDistances) {
                DedicatedArenaLayout.Coordinate point = layout.coordinate(lane, distance);
                int radiusSquared = point.x() * point.x() + point.z() * point.z();
                assertAll(lane + " @ " + distance,
                        () -> assertEquals(lane.xDirection() * distance, point.x()),
                        () -> assertEquals(lane.zDirection() * distance, point.z())
                );
                assertEquals(distance * distance, radiusSquared, lane + " 路线节点应保持在本路轴线上");
                assertTrue(
                        radiusSquared < previousRadiusSquared,
                        lane + " 路线节点应逐步靠近核心"
                );
                previousRadiusSquared = radiusSquared;
            }
        }
    }

    @Test
    void waypointContractCannotBeMutatedAndFloorHeightIsPreserved() {
        assertAll(
                () -> assertEquals(80, layout.floorY()),
                () -> assertEquals(List.of(56, 42), DedicatedArenaLayout.WAYPOINT_DISTANCES),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> DedicatedArenaLayout.WAYPOINT_DISTANCES.add(1))
        );
    }
}

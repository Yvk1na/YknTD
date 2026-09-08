package cn.ykntd.arena;

import cn.ykntd.model.Lane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingIslandGeometryTest {
    @Test
    void allFourAttackAxesAreContinuousFromSpawnToCore() {
        for (Lane lane : Lane.values()) {
            for (int distance = 0; distance <= DedicatedArenaLayout.SPAWN_DISTANCE; distance++) {
                int x = lane.xDirection() * distance;
                int z = lane.zDirection() * distance;
                assertNotEquals(FloatingIslandGeometry.Site.VOID,
                        FloatingIslandGeometry.siteAt(x, z), lane + " 路在 " + distance + " 格处断开");
            }
        }
    }

    @Test
    void navigationCorridorKeepsSevenClearFloorColumns() {
        for (Lane lane : Lane.values()) {
            for (int distance = 0; distance <= DedicatedArenaLayout.SPAWN_DISTANCE; distance++) {
                for (int side = -3; side <= 3; side++) {
                    Coordinate point = point(lane, distance, side);
                    assertNotEquals(FloatingIslandGeometry.Site.VOID,
                            FloatingIslandGeometry.siteAt(point.x(), point.z()),
                            lane + " 路在 " + distance + " 格、侧向 " + side + " 格处不安全");
                }
            }
        }
    }

    @Test
    void gameplayAnchorsStayOnTheirDedicatedPlatforms() {
        assertEquals(FloatingIslandGeometry.Site.CENTRAL,
                FloatingIslandGeometry.siteAt(0, 0));
        for (Lane lane : Lane.values()) {
            Coordinate ward = point(lane, DedicatedArenaLayout.WARD_DISTANCE, 0);
            Coordinate firstWaypoint = point(lane, 42, 0);
            Coordinate secondWaypoint = point(lane, 56, 0);
            Coordinate spawn = point(lane, DedicatedArenaLayout.SPAWN_DISTANCE, 0);
            assertEquals(FloatingIslandGeometry.Site.WARD,
                    FloatingIslandGeometry.siteAt(ward.x(), ward.z()));
            assertEquals(FloatingIslandGeometry.Site.WAYSTATION,
                    FloatingIslandGeometry.siteAt(firstWaypoint.x(), firstWaypoint.z()));
            assertEquals(FloatingIslandGeometry.Site.WAYSTATION,
                    FloatingIslandGeometry.siteAt(secondWaypoint.x(), secondWaypoint.z()));
            assertEquals(FloatingIslandGeometry.Site.OUTER,
                    FloatingIslandGeometry.siteAt(spawn.x(), spawn.z()));
        }
    }

    @Test
    void lobbyFiveByFiveAreaRemainsOnTheCentralIsland() {
        for (int x = DedicatedArenaLayout.LOBBY_X - 2; x <= DedicatedArenaLayout.LOBBY_X + 2; x++) {
            for (int z = DedicatedArenaLayout.LOBBY_Z - 2; z <= DedicatedArenaLayout.LOBBY_Z + 2; z++) {
                assertEquals(FloatingIslandGeometry.Site.CENTRAL,
                        FloatingIslandGeometry.siteAt(x, z));
            }
        }
    }

    @Test
    void bridgesAndWaystationsKeepWideSafePlatforms() {
        for (Lane lane : Lane.values()) {
            for (int side = -FloatingIslandGeometry.BRIDGE_HALF_WIDTH;
                 side <= FloatingIslandGeometry.BRIDGE_HALF_WIDTH; side++) {
                Coordinate point = point(lane, 48, side);
                assertEquals(FloatingIslandGeometry.Site.BRIDGE,
                        FloatingIslandGeometry.siteAt(point.x(), point.z()));
            }
            for (int side = -FloatingIslandGeometry.WAYSTATION_HALF_WIDTH;
                 side <= FloatingIslandGeometry.WAYSTATION_HALF_WIDTH; side++) {
                Coordinate point = point(lane, 42, side);
                assertEquals(FloatingIslandGeometry.Site.WAYSTATION,
                        FloatingIslandGeometry.siteAt(point.x(), point.z()));
            }
        }
    }

    @Test
    void fourDetachedSatelliteIslandsRemainInsideDefaultBorder() {
        for (int xSign : new int[]{-1, 1}) {
            for (int zSign : new int[]{-1, 1}) {
                int x = xSign * FloatingIslandGeometry.SATELLITE_OFFSET;
                int z = zSign * FloatingIslandGeometry.SATELLITE_OFFSET;
                assertEquals(FloatingIslandGeometry.Site.SATELLITE,
                        FloatingIslandGeometry.siteAt(x, z));
                assertTrue(Math.hypot(x, z) + FloatingIslandGeometry.SATELLITE_RADIUS < 88.0);
            }
        }
    }

    @Test
    void terrainFootprintIsRotationallySymmetricAndWithinMinimumBorder() {
        for (int x = -90; x <= 90; x++) {
            for (int z = -90; z <= 90; z++) {
                FloatingIslandGeometry.Site site = FloatingIslandGeometry.siteAt(x, z);
                assertEquals(site, FloatingIslandGeometry.siteAt(-z, x));
                if (site != FloatingIslandGeometry.Site.VOID) {
                    assertTrue(Math.abs(x) <= 86 && Math.abs(z) <= 86,
                            "地形越过最小世界边界: " + x + "," + z);
                }
            }
        }
    }

    private static Coordinate point(Lane lane, int forward, int side) {
        return new Coordinate(
                lane.xDirection() * forward - lane.zDirection() * side,
                lane.zDirection() * forward + lane.xDirection() * side);
    }

    private record Coordinate(int x, int z) {
    }
}

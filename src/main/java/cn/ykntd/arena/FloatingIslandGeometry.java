package cn.ykntd.arena;

import cn.ykntd.model.Lane;

final class FloatingIslandGeometry {
    private static final Lane[] LANES = Lane.values();
    static final int CENTRAL_RADIUS = 25;
    static final int OUTER_RADIUS = 16;
    static final int WARD_RADIUS = 8;
    static final int BRIDGE_HALF_WIDTH = 5;
    static final int WAYSTATION_HALF_LENGTH = 3;
    static final int WAYSTATION_HALF_WIDTH = 7;
    static final int SATELLITE_OFFSET = 38;
    static final int SATELLITE_RADIUS = 5;

    private FloatingIslandGeometry() {
    }

    static Site siteAt(int x, int z) {
        if (squared(x, z) <= squared(CENTRAL_RADIUS)) return Site.CENTRAL;

        for (Lane lane : LANES) {
            int forward = forward(lane, x, z);
            int side = perpendicular(lane, x, z);

            int wardDelta = forward - DedicatedArenaLayout.WARD_DISTANCE;
            if (squared(wardDelta, side) <= squared(WARD_RADIUS)) return Site.WARD;

            if (isWaystation(forward, side)) return Site.WAYSTATION;

            int outerDelta = forward - DedicatedArenaLayout.SPAWN_DISTANCE;
            if (squared(outerDelta, side) <= squared(OUTER_RADIUS)) return Site.OUTER;

            if (forward >= CENTRAL_RADIUS - 2
                    && forward <= DedicatedArenaLayout.SPAWN_DISTANCE
                    && Math.abs(side) <= BRIDGE_HALF_WIDTH) return Site.BRIDGE;
        }

        int satelliteX = x < 0 ? -SATELLITE_OFFSET : SATELLITE_OFFSET;
        int satelliteZ = z < 0 ? -SATELLITE_OFFSET : SATELLITE_OFFSET;
        if (squared(x - satelliteX, z - satelliteZ) <= squared(SATELLITE_RADIUS)) {
            return Site.SATELLITE;
        }
        return Site.VOID;
    }

    static int depthAt(int x, int z, Site site) {
        return switch (site) {
            case CENTRAL -> taperedDepth(CENTRAL_RADIUS, Math.hypot(x, z), 5, 19, 0.58);
            case WARD -> {
                Lane lane = laneAt(x, z);
                yield taperedDepth(WARD_RADIUS,
                        Math.hypot(forward(lane, x, z) - DedicatedArenaLayout.WARD_DISTANCE,
                                perpendicular(lane, x, z)), 5, 12, 0.92);
            }
            case OUTER -> {
                Lane lane = laneAt(x, z);
                yield taperedDepth(OUTER_RADIUS,
                        Math.hypot(forward(lane, x, z) - DedicatedArenaLayout.SPAWN_DISTANCE,
                                perpendicular(lane, x, z)), 5, 14, 0.58);
            }
            case WAYSTATION -> {
                Lane lane = laneAt(x, z);
                int distance = Math.abs(forward(lane, x, z) - nearestWaypoint(forward(lane, x, z)));
                yield 7 + Math.max(0, WAYSTATION_HALF_LENGTH - distance);
            }
            case BRIDGE -> {
                Lane lane = laneAt(x, z);
                int rib = Math.floorMod(forward(lane, x, z), 14);
                int ribDistance = Math.min(rib, 14 - rib);
                yield 5 + (ribDistance <= 1 ? 3 : 0)
                        + (Math.abs(perpendicular(lane, x, z)) == BRIDGE_HALF_WIDTH ? 1 : 0);
            }
            case SATELLITE -> {
                int centerX = x < 0 ? -SATELLITE_OFFSET : SATELLITE_OFFSET;
                int centerZ = z < 0 ? -SATELLITE_OFFSET : SATELLITE_OFFSET;
                yield taperedDepth(SATELLITE_RADIUS,
                        Math.hypot(x - centerX, z - centerZ), 4, 10, 1.15);
            }
            case VOID -> 0;
        };
    }

    static Lane laneAt(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) return x >= 0 ? Lane.EAST : Lane.WEST;
        return z >= 0 ? Lane.SOUTH : Lane.NORTH;
    }

    static int forward(Lane lane, int x, int z) {
        return x * lane.xDirection() + z * lane.zDirection();
    }

    static int perpendicular(Lane lane, int x, int z) {
        return x * -lane.zDirection() + z * lane.xDirection();
    }

    static int nearestWaypoint(int forward) {
        int nearest = DedicatedArenaLayout.WAYPOINT_DISTANCES.getFirst();
        int best = Math.abs(forward - nearest);
        for (int candidate : DedicatedArenaLayout.WAYPOINT_DISTANCES) {
            int distance = Math.abs(forward - candidate);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isWaystation(int forward, int side) {
        if (Math.abs(side) > WAYSTATION_HALF_WIDTH) return false;
        for (int waypoint : DedicatedArenaLayout.WAYPOINT_DISTANCES) {
            if (Math.abs(forward - waypoint) <= WAYSTATION_HALF_LENGTH) return true;
        }
        return false;
    }

    private static int taperedDepth(int radius, double distance, int minimum,
                                    int maximum, double slope) {
        int depth = minimum + (int) Math.floor(Math.max(0.0, radius - distance) * slope);
        return Math.clamp(depth, minimum, maximum);
    }

    private static long squared(int value) {
        return (long) value * value;
    }

    private static long squared(int first, int second) {
        return (long) first * first + (long) second * second;
    }

    enum Site {
        VOID,
        CENTRAL,
        BRIDGE,
        WAYSTATION,
        WARD,
        OUTER,
        SATELLITE
    }
}

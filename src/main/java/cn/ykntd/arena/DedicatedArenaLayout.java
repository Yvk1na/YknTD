package cn.ykntd.arena;

import cn.ykntd.model.Lane;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class DedicatedArenaLayout {
    public static final int WARD_DISTANCE = 29;
    public static final int SPAWN_DISTANCE = 70;
    public static final List<Integer> WAYPOINT_DISTANCES = List.of(56, 42);
    public static final int LOBBY_X = 11;
    public static final int LOBBY_Z = 11;

    private final int floorY;

    public DedicatedArenaLayout(int floorY) {
        this.floorY = floorY;
    }

    public ArenaDefinition createDefinition(String arenaId, World world) {
        ArenaDefinition arena = new ArenaDefinition(arenaId);
        arena.lobby(new Location(world, LOBBY_X + 0.5, floorY + 1.0,
                LOBBY_Z + 0.5, 135.0f, 0.0f));
        arena.core(blockCenter(world, 0, 0));
        for (Lane lane : Lane.values()) {
            arena.spawn(lane, feet(world, lane, SPAWN_DISTANCE));
            arena.ward(lane, blockCenter(world,
                    lane.xDirection() * WARD_DISTANCE,
                    lane.zDirection() * WARD_DISTANCE));
            List<Location> waypoints = new ArrayList<>();
            for (int distance : WAYPOINT_DISTANCES) waypoints.add(feet(world, lane, distance));
            arena.setWaypoints(lane, waypoints);
        }
        return arena;
    }

    public Coordinate coordinate(Lane lane, int distance) {
        return new Coordinate(lane.xDirection() * distance, lane.zDirection() * distance);
    }

    public int floorY() {
        return floorY;
    }

    private Location feet(World world, Lane lane, int distance) {
        Coordinate point = coordinate(lane, distance);
        float yaw = switch (lane) {
            case NORTH -> 0.0f;
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> -90.0f;
        };
        return new Location(world, point.x() + 0.5, floorY + 1.0, point.z() + 0.5, yaw, 0.0f);
    }

    private Location blockCenter(World world, int x, int z) {
        return new Location(world, x + 0.5, floorY + 1.5, z + 0.5);
    }

    public record Coordinate(int x, int z) {
    }
}

package cn.ykntd.arena;

import cn.ykntd.model.Lane;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;

public final class ArenaDefinition {
    private final String id;
    private Location lobby;
    private Location core;
    private final EnumMap<Lane, Location> spawns = new EnumMap<>(Lane.class);
    private final EnumMap<Lane, Location> wards = new EnumMap<>(Lane.class);
    private final EnumMap<Lane, List<Location>> waypoints = new EnumMap<>(Lane.class);

    public ArenaDefinition(String id) {
        this.id = id;
        for (Lane lane : Lane.values()) waypoints.put(lane, new ArrayList<>());
    }

    public String id() {
        return id;
    }

    public Location lobby() {
        return cloneLocation(lobby);
    }

    public void lobby(Location value) {
        lobby = cloneLocation(value);
    }

    public Location core() {
        return cloneLocation(core);
    }

    public void core(Location value) {
        core = cloneLocation(value);
    }

    public Location spawn(Lane lane) {
        return cloneLocation(spawns.get(lane));
    }

    public void spawn(Lane lane, Location value) {
        spawns.put(lane, cloneLocation(value));
    }

    public Location ward(Lane lane) {
        return cloneLocation(wards.get(lane));
    }

    public void ward(Lane lane, Location value) {
        wards.put(lane, cloneLocation(value));
    }

    public Map<Lane, Location> spawns() {
        return cloneMap(spawns);
    }

    public Map<Lane, Location> wards() {
        return cloneMap(wards);
    }

    public List<Location> waypoints(Lane lane) {
        return waypoints.getOrDefault(lane, List.of()).stream()
                .map(Location::clone)
                .toList();
    }

    public void setWaypoints(Lane lane, List<Location> values) {
        waypoints.put(lane, values.stream().map(Location::clone).collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    }

    public void addWaypoint(Lane lane, Location value) {
        waypoints.computeIfAbsent(lane, ignored -> new ArrayList<>()).add(value.clone());
    }

    public void clearWaypoints(Lane lane) {
        waypoints.computeIfAbsent(lane, ignored -> new ArrayList<>()).clear();
    }

    public List<String> problems() {
        return problems(Double.POSITIVE_INFINITY);
    }

    public List<String> problems(double boundaryRadius) {
        LinkedHashSet<String> problems = new LinkedHashSet<>();
        if (!valid(lobby)) problems.add("大厅位置");
        if (!valid(core)) problems.add("核心位置");
        for (Lane lane : Lane.values()) {
            Location spawn = spawns.get(lane);
            Location ward = wards.get(lane);
            if (!valid(spawn)) problems.add(lane.displayName() + "出生点");
            if (!valid(ward)) problems.add(lane.displayName() + "结界点");
        }
        World expected = valid(core) ? core.getWorld() : null;
        if (expected != null) {
            validatePoint(problems, expected, core, boundaryRadius, "核心");
            validatePoint(problems, expected, lobby, boundaryRadius, "大厅");
            for (Lane lane : Lane.values()) {
                validatePoint(problems, expected, spawns.get(lane), boundaryRadius, lane.displayName() + "出生点");
                validatePoint(problems, expected, wards.get(lane), boundaryRadius, lane.displayName() + "结界点");
                List<Location> path = waypoints.getOrDefault(lane, List.of());
                for (int index = 0; index < path.size(); index++) {
                    validatePoint(problems, expected, path.get(index), boundaryRadius,
                            lane.displayName() + "路点 " + (index + 1));
                }
            }
        }
        return List.copyOf(problems);
    }

    public boolean complete() {
        return problems().isEmpty();
    }

    public boolean contains(Location location, double radius) {
        if (core == null || location == null || !sameWorld(core.getWorld(), location)) return false;
        return core.distanceSquared(location) <= radius * radius;
    }

    private static boolean sameWorld(World world, Location location) {
        return location != null && location.getWorld() != null && location.getWorld().equals(world);
    }

    private void validatePoint(LinkedHashSet<String> problems, World expected, Location location,
                               double boundaryRadius, String label) {
        if (!valid(location)) return;
        if (!sameWorld(expected, location)) {
            problems.add(label + "不在核心世界");
            return;
        }
        if (Double.isFinite(boundaryRadius) && boundaryRadius > 0.0
                && core.distanceSquared(location) > boundaryRadius * boundaryRadius) {
            problems.add(label + "超出场地边界");
        }
    }

    private static boolean valid(Location location) {
        return location != null && location.getWorld() != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }

    private static Location cloneLocation(Location value) {
        return value == null ? null : value.clone();
    }

    private static Map<Lane, Location> cloneMap(Map<Lane, Location> source) {
        EnumMap<Lane, Location> result = new EnumMap<>(Lane.class);
        source.forEach((lane, location) -> result.put(lane, cloneLocation(location)));
        return Map.copyOf(result);
    }
}

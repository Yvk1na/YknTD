package cn.ykntd.arena;

import cn.ykntd.model.Lane;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ArenaRepository {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, ArenaDefinition> arenas = new LinkedHashMap<>();
    private YamlConfiguration yaml;

    public ArenaRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "arenas.yml");
        reload();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
        arenas.clear();
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String id = normalize(rawId);
            if (!validId(id)) {
                plugin.getLogger().warning("忽略 arenas.yml 中的非法场地 ID：" + rawId);
                continue;
            }
            // Generated arenas are reconstructed after their Multiverse world has loaded.
            // Keeping Bukkit Location objects out of YAML avoids deserialization against an unloaded world.
            if (root.getBoolean(rawId + ".generated", false)) continue;
            ArenaDefinition arena = new ArenaDefinition(id);
            arena.lobby(root.getLocation(rawId + ".lobby"));
            arena.core(root.getLocation(rawId + ".core"));
            for (Lane lane : Lane.values()) {
                arena.spawn(lane, root.getLocation(rawId + ".lanes." + lane.key() + ".spawn"));
                arena.ward(lane, root.getLocation(rawId + ".lanes." + lane.key() + ".ward"));
                List<Location> waypoints = root.getList(rawId + ".lanes." + lane.key() + ".waypoints", List.of())
                        .stream()
                        .filter(Location.class::isInstance)
                        .map(Location.class::cast)
                        .toList();
                arena.setWaypoints(lane, waypoints);
            }
            arenas.put(id, arena);
        }
    }

    public Collection<ArenaDefinition> all() {
        return List.copyOf(arenas.values());
    }

    public Optional<ArenaDefinition> find(String id) {
        return Optional.ofNullable(arenas.get(normalize(id)));
    }

    public boolean create(String rawId) {
        String id = normalize(rawId);
        if (!validId(id) || arenas.containsKey(id) || yaml.contains("arenas." + id)) return false;
        arenas.put(id, new ArenaDefinition(id));
        yaml.createSection("arenas." + id);
        save();
        return true;
    }

    public boolean delete(String rawId) {
        String id = normalize(rawId);
        if (arenas.remove(id) == null) return false;
        yaml.set("arenas." + id, null);
        save();
        return true;
    }

    public void installGenerated(ArenaDefinition arena) {
        String id = normalize(arena.id());
        if (!validId(id)) throw new IllegalArgumentException("非法场地 ID：" + arena.id());
        if (!canInstallGenerated(id)) {
            throw new IllegalStateException("场地 ID " + id
                    + " 已被手工场地使用；请修改 dedicated-arena.arena-id，原配置未被覆盖");
        }
        arenas.put(id, arena);
        String path = "arenas." + id;
        yaml.set(path, null);
        yaml.createSection(path);
        yaml.set(path + ".generated", true);
        yaml.set(path + ".world", arena.core().getWorld().getName());
        save();
    }

    public boolean canInstallGenerated(String rawId) {
        String path = "arenas." + normalize(rawId);
        YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file);
        return generatedOrAbsent(yaml, path) && generatedOrAbsent(persisted, path);
    }

    private static boolean generatedOrAbsent(YamlConfiguration source, String path) {
        return !source.contains(path) || source.getBoolean(path + ".generated", false);
    }

    public void setLobby(ArenaDefinition arena, Location location) {
        arena.lobby(location);
        setLocation(arena.id() + ".lobby", location);
    }

    public void setCore(ArenaDefinition arena, Location location) {
        arena.core(location);
        setLocation(arena.id() + ".core", location);
    }

    public void setSpawn(ArenaDefinition arena, Lane lane, Location location) {
        arena.spawn(lane, location);
        setLocation(arena.id() + ".lanes." + lane.key() + ".spawn", location);
    }

    public void setWard(ArenaDefinition arena, Lane lane, Location location) {
        arena.ward(lane, location);
        setLocation(arena.id() + ".lanes." + lane.key() + ".ward", location);
    }

    public void addWaypoint(ArenaDefinition arena, Lane lane, Location location) {
        arena.addWaypoint(lane, location);
        yaml.set("arenas." + arena.id() + ".lanes." + lane.key() + ".waypoints", arena.waypoints(lane));
        save();
    }

    public void clearWaypoints(ArenaDefinition arena, Lane lane) {
        arena.clearWaypoints(lane);
        yaml.set("arenas." + arena.id() + ".lanes." + lane.key() + ".waypoints", List.of());
        save();
    }

    private void setLocation(String path, Location location) {
        yaml.set("arenas." + path, location == null ? null : location.clone());
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存 " + file.getAbsolutePath(), exception);
        }
    }

    public static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }
}

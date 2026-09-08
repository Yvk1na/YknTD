package cn.ykntd.stats;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class RecoveryRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public RecoveryRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "recovery.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void remember(UUID playerId, Location location) {
        if (location == null || location.getWorld() == null) return;
        yaml.set("players." + playerId, location.clone());
        save();
    }

    public Optional<Location> get(UUID playerId) {
        Location location = yaml.getLocation("players." + playerId);
        return Optional.ofNullable(location).map(Location::clone);
    }

    public void clear(UUID playerId) {
        yaml.set("players." + playerId, null);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存夜袭返回点：" + exception.getMessage());
        }
    }
}

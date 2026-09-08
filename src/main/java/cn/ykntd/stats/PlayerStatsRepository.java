package cn.ykntd.stats;

import cn.ykntd.model.Contribution;
import cn.ykntd.model.ContributionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerStatsRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PlayerStatsRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "players.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void record(UUID playerId, String name, boolean victory, int wave,
                       Contribution contribution, double score) {
        String path = "players." + playerId;
        yaml.set(path + ".name", name);
        yaml.set(path + ".games", yaml.getInt(path + ".games") + 1);
        if (victory) yaml.set(path + ".wins", yaml.getInt(path + ".wins") + 1);
        yaml.set(path + ".best-wave", Math.max(wave, yaml.getInt(path + ".best-wave")));
        yaml.set(path + ".total-score", yaml.getDouble(path + ".total-score") + score);
        for (ContributionType type : ContributionType.values()) {
            String metric = path + ".contribution." + type.key();
            yaml.set(metric, yaml.getDouble(metric) + contribution.value(type));
        }
        save();
    }

    public Optional<PlayerStats> get(UUID playerId) {
        String path = "players." + playerId;
        if (!yaml.isConfigurationSection(path)) return Optional.empty();
        return Optional.of(read(playerId, path));
    }

    public Optional<PlayerStats> findByName(String name) {
        if (name == null) return Optional.empty();
        return all().stream().filter(stats -> stats.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<PlayerStats> top(int limit) {
        return all().stream()
                .sorted(Comparator.comparingDouble(PlayerStats::totalScore).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerStats::wins).reversed()))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<PlayerStats> all() {
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return List.of();
        List<PlayerStats> result = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                result.add(read(id, "players." + key));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("players.yml 中存在无效 UUID：" + key);
            }
        }
        return result;
    }

    private PlayerStats read(UUID id, String path) {
        return new PlayerStats(
                id,
                yaml.getString(path + ".name", id.toString()),
                yaml.getInt(path + ".games"),
                yaml.getInt(path + ".wins"),
                yaml.getInt(path + ".best-wave"),
                yaml.getDouble(path + ".total-score"));
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存玩家统计：" + exception.getMessage());
        }
    }
}

package cn.ykntd.reward;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class PendingRewardRepository {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PendingRewardRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "pending-rewards.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void put(UUID playerId, RewardService.RewardPlan plan) {
        if (!plan.eligible()) return;
        String path = "players." + playerId;
        yaml.set(path + ".score", plan.score());
        yaml.set(path + ".tier", plan.tierName());
        yaml.set(path + ".commands", plan.commands());
        save();
    }

    public Optional<RewardService.RewardPlan> take(UUID playerId) {
        String path = "players." + playerId;
        if (!yaml.isConfigurationSection(path)) return Optional.empty();
        RewardService.RewardPlan plan = new RewardService.RewardPlan(
                yaml.getDouble(path + ".score"),
                yaml.getString(path + ".tier", "离线奖励"),
                true,
                yaml.getStringList(path + ".commands"));
        // Remove before command execution: a crash can lose a reward, but cannot duplicate it.
        yaml.set(path, null);
        save();
        return Optional.of(plan);
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存离线奖励：" + exception.getMessage());
        }
    }
}

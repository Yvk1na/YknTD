package cn.ykntd.reward;

import cn.ykntd.model.Contribution;
import cn.ykntd.model.ContributionType;
import cn.ykntd.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RewardService {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");
    private final JavaPlugin plugin;

    public RewardService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public RewardResult deliver(Player player, String arena, boolean victory, int wave,
                                Contribution contribution,
                                Map<ContributionType, Double> weights,
                                double minimumScore,
                                int participantCount) {
        return execute(prepare(player.getUniqueId(), player.getName(), arena, victory, wave,
                contribution, weights, minimumScore, participantCount));
    }

    public RewardPlan prepare(UUID playerId, String playerName, String arena, boolean victory, int wave,
                              Contribution contribution,
                              Map<ContributionType, Double> weights,
                              double minimumScore,
                              int participantCount) {
        double score = contribution.score(weights);
        if (score + 0.0001 < minimumScore) {
            return new RewardPlan(score, "未达门槛", false, List.of());
        }

        List<String> commands = new ArrayList<>();
        String tierName = victory ? "参与" : "失败补偿";
        if (victory) {
            commands.addAll(plugin.getConfig().getStringList("rewards.victory.base-commands"));
            RewardTier tier = highestTier(score);
            if (tier != null) {
                tierName = tier.name();
                commands.addAll(tier.commands());
            }
        } else {
            commands.addAll(plugin.getConfig().getStringList("rewards.failure.commands"));
        }

        Map<String, Object> placeholders = placeholders(
                playerId, playerName, arena, wave, contribution, score, participantCount);
        List<String> prepared = new ArrayList<>();
        for (String configured : commands) {
            String command = Text.placeholders(configured, placeholders).trim();
            while (command.startsWith("/")) command = command.substring(1);
            if (!command.isBlank()) prepared.add(command);
        }
        return new RewardPlan(score, tierName, true, prepared);
    }

    public RewardResult execute(RewardPlan plan) {
        if (!plan.eligible()) return new RewardResult(plan.score(), plan.tierName(), false);
        for (String command : plan.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        return new RewardResult(plan.score(), plan.tierName(), true);
    }

    private RewardTier highestTier(double score) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("rewards.victory.tiers");
        if (root == null) return null;
        return root.getKeys(false).stream()
                .map(key -> new RewardTier(
                        root.getString(key + ".display-name", key),
                        root.getDouble(key + ".minimum-score", Double.MAX_VALUE),
                        root.getStringList(key + ".commands")))
                .filter(tier -> score + 0.0001 >= tier.minimumScore())
                .max(Comparator.comparingDouble(RewardTier::minimumScore))
                .orElse(null);
    }

    private static Map<String, Object> placeholders(UUID playerId, String playerName,
                                                     String arena, int wave,
                                                     Contribution contribution, double score,
                                                     int participantCount) {
        Map<String, Object> values = new HashMap<>();
        values.put("player", playerName);
        values.put("uuid", playerId);
        values.put("arena", arena);
        values.put("wave", wave);
        values.put("players", participantCount);
        values.put("score", NUMBER.format(score));
        for (ContributionType type : ContributionType.values()) {
            values.put(type.key(), NUMBER.format(contribution.value(type)));
        }
        return values;
    }

    private record RewardTier(String name, double minimumScore, List<String> commands) {
    }

    public record RewardPlan(double score, String tierName, boolean eligible, List<String> commands) {
        public RewardPlan {
            score = Math.max(0.0, score);
            tierName = tierName == null ? "未知" : tierName;
            commands = List.copyOf(commands);
        }
    }

    public record RewardResult(double score, String tierName, boolean delivered) {
    }
}

package cn.ykntd.config;

import cn.ykntd.model.Lane;
import cn.ykntd.model.MobGroup;
import cn.ykntd.model.MobProvider;
import cn.ykntd.model.PowerStage;
import cn.ykntd.model.WaveDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class WaveRepository {
    private final JavaPlugin plugin;
    private final File file;
    private List<WaveDefinition> waves = List.of();

    public WaveRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "waves.yml");
        reload();
    }

    public void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("waves");
        if (root == null) throw new IllegalStateException("waves.yml 缺少 waves 根节点");
        List<WaveDefinition> loaded = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("忽略非数字波次：" + key);
                continue;
            }
            ConfigurationSection wave = root.getConfigurationSection(key);
            if (wave == null) continue;
            List<MobGroup> groups = new ArrayList<>();
            for (var raw : wave.getMapList("groups")) {
                try {
                    Object providerValue = raw.containsKey("provider") ? raw.get("provider") : "VANILLA";
                    MobProvider provider = MobProvider.parse(String.valueOf(providerValue));
                    Object idValue = raw.containsKey("id") ? raw.get("id") : raw.get("mob");
                    String id = idValue == null ? "" : idValue.toString().trim();
                    List<Lane> lanes = parseLanes(raw.get("lanes"), raw.get("lane"));
                    int amount = integer(raw.get("amount"), 1);
                    int perLane = integer(raw.get("amount-per-lane"), 0);
                    if (perLane > 0) amount = perLane * lanes.size();
                    groups.add(new MobGroup(
                            provider,
                            id,
                            amount,
                            lanes,
                            decimal(raw.get("level"), 1.0),
                            decimal(raw.get("base-health"), 0.0),
                            decimal(raw.get("base-damage"), 0.0),
                            decimal(raw.get("health-multiplier"), 1.0),
                            decimal(raw.get("damage-multiplier"), 1.0),
                            decimal(raw.get("ward-damage"), 4.0),
                            bool(raw.get("boss"), false)));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("waves.yml 第 " + number + " 波怪物组无效：" + exception.getMessage(), exception);
                }
            }
            int defaultLimit = plugin.getConfig().getInt("game.wave-time-limit-seconds", 240);
            loaded.add(new WaveDefinition(
                    number,
                    wave.getString("name", "第 " + number + " 波"),
                    PowerStage.parse(wave.getString("stage"), number),
                    wave.getInt("recommended-aura-level", 0),
                    Math.max(1, wave.getInt("spawn-interval-ticks", 12)),
                    Math.max(20, wave.getInt("time-limit-seconds", defaultLimit) * 20),
                    groups));
        }
        loaded.sort(Comparator.comparingInt(WaveDefinition::number));
        if (loaded.isEmpty()) throw new IllegalStateException("waves.yml 至少需要一个有效波次");
        for (int index = 0; index < loaded.size(); index++) {
            if (loaded.get(index).number() != index + 1) {
                throw new IllegalStateException("waves.yml 波次必须从 1 开始连续编号");
            }
        }
        waves = List.copyOf(loaded);
    }

    public List<WaveDefinition> all() {
        return waves;
    }

    private static List<Lane> parseLanes(Object rawLanes, Object rawLane) {
        List<String> values = new ArrayList<>();
        if (rawLanes instanceof Iterable<?> iterable) {
            for (Object value : iterable) values.add(String.valueOf(value));
        } else if (rawLanes != null) {
            values.addAll(Arrays.asList(rawLanes.toString().split(",")));
        } else if (rawLane != null) {
            values.add(rawLane.toString());
        }
        if (values.isEmpty()) return List.of(Lane.values());
        List<Lane> result = values.stream()
                .map(value -> Lane.parse(value).orElseThrow(() ->
                        new IllegalArgumentException("未知路线：" + value)))
                .distinct()
                .toList();
        return result.isEmpty() ? List.of(Lane.values()) : result;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
}

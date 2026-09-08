package cn.ykntd.hud;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record RaidSidebarSettings(boolean enabled, String title, List<String> lines) {
    private static final String SKYBLOCK_TITLE = "&b&lSKYBLOCK";
    private static final String LEGACY_RAID_TITLE = "&5&l浮岛夜袭";
    private static final List<String> DEFAULT_LINES = List.of(
            "&8&m──────────────",
            "&7状态 &f{state}",
            "&7波次 &d{wave}&7/&f{total-waves} &8│ &7敌人 &c{enemies}",
            "",
            "&b▌ 北路 {north}",
            "&6▌ 东路 {east}",
            "&a▌ 南路 {south}",
            "&d▌ 西路 {west}",
            "",
            "&c❤ 核心 {core}",
            "&7队伍 &f{players}&7/&f{max-players}",
            "&8&m──────────────",
            "&emc.infnetwork.net");

    public RaidSidebarSettings {
        if (title == null || title.isBlank() || LEGACY_RAID_TITLE.equalsIgnoreCase(title.trim())) {
            title = SKYBLOCK_TITLE;
        }
        List<String> source = lines == null || lines.isEmpty() ? DEFAULT_LINES : lines;
        lines = source.stream()
                .limit(15)
                .map(line -> line == null ? "" : line)
                .toList();
    }

    public static RaidSidebarSettings load(FileConfiguration config) {
        return new RaidSidebarSettings(
                config.getBoolean("sidebar.enabled", true),
                config.getString("sidebar.title", SKYBLOCK_TITLE),
                config.getStringList("sidebar.lines"));
    }
}

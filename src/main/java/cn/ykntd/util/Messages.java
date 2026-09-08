package cn.ykntd.util;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class Messages {
    private Messages() {
    }

    public static void send(JavaPlugin plugin, CommandSender target, String path,
                            String fallback, Map<String, ?> placeholders) {
        String configured = plugin.getConfig().getString("messages." + path, fallback);
        configured = PlayerMessagePolicy.sanitizeTemplate(path, configured, fallback);
        String prefix = plugin.getConfig().getString("messages.prefix", "&5&l夜袭 &8» &r");
        target.sendMessage(Text.component(prefix + Text.placeholders(configured, placeholders)));
    }

    public static void send(JavaPlugin plugin, CommandSender target, String path, String fallback) {
        send(plugin, target, path, fallback, Map.of());
    }
}

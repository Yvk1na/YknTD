package cn.ykntd.arena;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ArenaWorldStorage {
    public static final String ROOT_FOLDER = "tdmap";

    private ArenaWorldStorage() {
    }

    public static String instanceWorldName(String worldBaseName, int slot) {
        if (slot < 1 || slot > 99) throw new IllegalArgumentException("竞技场实例编号必须在 1～99 之间");
        return ROOT_FOLDER + "/" + worldBaseName + "_" + String.format(Locale.ROOT, "%02d", slot);
    }

    public static Path ensureRootDirectory() {
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path root = worldContainer.resolve(ROOT_FOLDER).normalize();
        if (!root.startsWith(worldContainer)) {
            throw new IllegalStateException("夜袭世界目录超出服务端世界容器：" + root);
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建夜袭世界目录 " + root, exception);
        }
        return root;
    }

    public static Path worldDirectory(DedicatedArenaSettings settings, int slot) {
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path root = ensureRootDirectory();
        Path directory = worldContainer.resolve(settings.managedWorldName(slot)).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("夜袭世界目录超出 tdmap：" + settings.managedWorldName(slot));
        }
        return directory;
    }

    public static Path legacyWorldDirectory(DedicatedArenaSettings settings) {
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path root = ensureRootDirectory();
        Path directory = worldContainer.resolve(settings.legacyManagedWorldName()).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("旧夜袭世界目录超出 tdmap：" + settings.legacyManagedWorldName());
        }
        return directory;
    }
}

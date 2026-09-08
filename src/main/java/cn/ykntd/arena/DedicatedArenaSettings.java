package cn.ykntd.arena;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public record DedicatedArenaSettings(
        boolean enabled,
        String arenaId,
        String worldName,
        int instanceCount,
        int radius,
        int floorY,
        long seed,
        boolean autoCreate,
        boolean resetBeforeSession
) {
    private static final Pattern WORLD_NAME = Pattern.compile("[a-z0-9._-]{1,48}");

    public static DedicatedArenaSettings load(FileConfiguration config) {
        String root = "dedicated-arena.";
        String arenaId = ArenaRepository.normalize(config.getString(root + "arena-id", "night_raid"));
        if (!ArenaRepository.validId(arenaId)) {
            throw new IllegalArgumentException("dedicated-arena.arena-id 不是合法场地 ID");
        }
        if (arenaId.length() > 29) {
            throw new IllegalArgumentException("dedicated-arena.arena-id 最长为 29 个字符，以便生成实例编号");
        }
        String worldName = config.getString(root + "world-name", "floating_island_night_raid")
                .trim().toLowerCase(Locale.ROOT);
        if (!WORLD_NAME.matcher(worldName).matches() || worldName.equals(".") || worldName.equals("..")) {
            throw new IllegalArgumentException("dedicated-arena.world-name 只能包含小写字母、数字、点、下划线和连字符");
        }
        return new DedicatedArenaSettings(
                config.getBoolean(root + "enabled", true),
                arenaId,
                worldName,
                clamp(config.getInt(root + "instance-count", 10), 1, 10),
                clamp(config.getInt(root + "radius", 96), 88, 256),
                clamp(config.getInt(root + "floor-y", 80), 32, 240),
                config.getLong(root + "seed", 731946285104L),
                config.getBoolean(root + "auto-create", false),
                config.getBoolean(root + "reset-before-session", true)
        );
    }

    public String managedWorldName(int slot) {
        validateSlot(slot);
        return ArenaWorldStorage.instanceWorldName(worldName, slot);
    }

    public String legacyManagedWorldName() {
        return ArenaWorldStorage.ROOT_FOLDER + "/" + worldName;
    }

    public String instanceArenaId(int slot) {
        validateSlot(slot);
        return arenaId + "_" + String.format(Locale.ROOT, "%02d", slot);
    }

    public OptionalInt instanceSlot(String rawId) {
        String normalized = ArenaRepository.normalize(rawId);
        for (int slot = 1; slot <= instanceCount; slot++) {
            if (instanceArenaId(slot).equals(normalized)) return OptionalInt.of(slot);
        }
        return OptionalInt.empty();
    }

    public boolean isInternalInstanceId(String rawId) {
        String normalized = ArenaRepository.normalize(rawId);
        for (int slot = 1; slot <= 10; slot++) {
            String internalId = arenaId + "_" + String.format(Locale.ROOT, "%02d", slot);
            if (internalId.equals(normalized)) return true;
        }
        return false;
    }

    public boolean isManagedWorldName(String candidate) {
        if (candidate == null) return false;
        for (int slot = 1; slot <= instanceCount; slot++) {
            if (managedWorldName(slot).equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    public String generationFingerprint() {
        return DedicatedArenaWorldManager.GENERATION_VERSION + ":" + floorY + ":" + seed;
    }

    public boolean sameGeneratedWorld(DedicatedArenaSettings other) {
        return other != null
                && arenaId.equals(other.arenaId)
                && worldName.equals(other.worldName)
                && instanceCount == other.instanceCount
                && floorY == other.floorY
                && seed == other.seed;
    }

    private void validateSlot(int slot) {
        if (slot < 1 || slot > instanceCount) {
            throw new IllegalArgumentException("竞技场实例编号必须在 1～" + instanceCount + " 之间");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

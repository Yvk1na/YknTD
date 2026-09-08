package cn.ykntd.arena;

import cn.ykntd.YknTdPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldType;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.persistence.PersistentDataType;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;
import org.mvplugins.multiverse.core.world.options.LoadWorldOptions;
import org.mvplugins.multiverse.core.world.options.RegenWorldOptions;
import org.mvplugins.multiverse.core.world.options.RemoveWorldOptions;
import org.mvplugins.multiverse.core.world.options.UnloadWorldOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DedicatedArenaWorldManager {
    public static final String GENERATOR_ID = "floating_island_night_raid";
    public static final int GENERATION_VERSION = 3;
    private static final String MARKER_FILE = ".ykntd-arena";

    private final YknTdPlugin plugin;
    private final NamespacedKey generationKey;
    private DedicatedArenaSettings settings;

    public DedicatedArenaWorldManager(YknTdPlugin plugin, DedicatedArenaSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.generationKey = new NamespacedKey(plugin, "arena_generation");
    }

    public void reload(DedicatedArenaSettings settings) {
        this.settings = settings;
    }

    public List<World> createAll() {
        List<World> worlds = new ArrayList<>(settings.instanceCount());
        for (int slot = 1; slot <= settings.instanceCount(); slot++) worlds.add(createOrLoad(slot));
        return List.copyOf(worlds);
    }

    public void migrateLegacyWorld() {
        requireEnabled();
        migrateLegacySingleWorld(worldManager());
    }

    public World createOrLoad(int slot) {
        validateSlot(slot);
        return createOrLoadInternal(slot, true).world();
    }

    private LoadResult createOrLoadInternal(int slot, boolean upgradeGeneration) {
        requireEnabled();
        WorldManager manager = worldManager();
        ArenaWorldStorage.ensureRootDirectory();
        if (slot == 1) migrateLegacySingleWorld(manager);
        String worldName = settings.managedWorldName(slot);
        LoadedMultiverseWorld managed = manager.getLoadedWorld(worldName).getOrNull();
        boolean created = false;

        if (managed == null && manager.isWorld(worldName)) {
            MultiverseWorld known = manager.getWorld(worldName).getOrNull();
            if (known == null) throw new IllegalStateException("Multiverse 找不到已登记的夜袭世界 " + worldName);
            assertManagedWorld(known);
            var loaded = manager.loadWorld(LoadWorldOptions.world(known).doFolderCheck(false));
            if (loaded.isFailure()) {
                throw new IllegalStateException("Multiverse 无法加载夜袭世界 " + worldName + "：" + loaded.getFailureReason());
            }
            managed = loaded.getOrNull();
        }

        if (managed == null) {
            Path folder = ArenaWorldStorage.worldDirectory(settings, slot);
            if (Files.isDirectory(folder)) {
                if (!Files.isRegularFile(folder.resolve(MARKER_FILE))) {
                    throw new IllegalStateException("同名世界目录并非由 YknTD 创建，拒绝接管：" + folder);
                }
                var imported = manager.importWorld(ImportWorldOptions.worldName(worldName)
                        .environment(World.Environment.NORMAL)
                        .generator(generatorName())
                        .useSpawnAdjust(false)
                        .doFolderCheck(false));
                if (imported.isFailure()) {
                    throw new IllegalStateException("Multiverse 无法导入夜袭世界 " + worldName + "："
                            + imported.getFailureReason());
                }
                managed = imported.getOrNull();
            } else {
                managed = createManagedWorld(manager, worldName);
                created = true;
            }
        }

        if (managed == null) throw new IllegalStateException("Multiverse 未返回夜袭世界 " + worldName);
        World world = managed.getBukkitWorld().getOrNull();
        if (world == null) throw new IllegalStateException("夜袭世界尚未加载：" + worldName);
        assertManagedWorld(managed);
        configureMultiverse(manager, managed);
        configureWorld(world);
        world.getChunkAt(0, 0).load(true);

        String fingerprint = world.getPersistentDataContainer().get(generationKey, PersistentDataType.STRING);
        if (created) {
            markGeneration(world, slot);
        } else if (upgradeGeneration
                && (fingerprint == null || !fingerprint.equals(settings.generationFingerprint()))) {
            if (!world.getPlayers().isEmpty()) {
                throw new IllegalStateException("夜袭竞技场实例 " + slot + " 版本已变化，但世界中仍有玩家，无法重建");
            }
            world = regenerate(manager, managed, slot);
        } else {
            ensureMarker(slot);
        }
        return new LoadResult(world, created);
    }

    public World regenerate(int slot) {
        validateSlot(slot);
        LoadResult loaded = createOrLoadInternal(slot, false);
        World world = loaded.world();
        if (loaded.created()) return world;
        if (!world.getPlayers().isEmpty()) {
            throw new IllegalStateException("夜袭竞技场实例 " + slot + " 中仍有玩家，不能重置");
        }
        WorldManager manager = worldManager();
        LoadedMultiverseWorld managed = manager.getLoadedWorld(world).getOrNull();
        if (managed == null) throw new IllegalStateException("夜袭世界未被 Multiverse 管理：" + world.getName());
        return regenerate(manager, managed, slot);
    }

    public List<World> regenerateAll() {
        List<World> worlds = new ArrayList<>(settings.instanceCount());
        for (int slot = 1; slot <= settings.instanceCount(); slot++) worlds.add(regenerate(slot));
        return List.copyOf(worlds);
    }

    public boolean exists() {
        if (!settings.enabled() || !MultiverseCoreApi.isLoaded()) return false;
        WorldManager manager = MultiverseCoreApi.get().getWorldManager();
        if (manager.isWorld(settings.legacyManagedWorldName())
                || Files.isDirectory(ArenaWorldStorage.legacyWorldDirectory(settings))) return true;
        for (int slot = 1; slot <= settings.instanceCount(); slot++) {
            if (manager.isWorld(settings.managedWorldName(slot))
                    || Files.isDirectory(ArenaWorldStorage.worldDirectory(settings, slot))) return true;
        }
        return false;
    }

    public boolean isDedicatedWorld(World world) {
        if (world == null || !isExpectedWorldName(world.getName()) || !MultiverseCoreApi.isLoaded()) return false;
        LoadedMultiverseWorld managed = MultiverseCoreApi.get().getWorldManager()
                .getLoadedWorld(world).getOrNull();
        return managed != null && generatorName().equalsIgnoreCase(managed.getGenerator());
    }

    public boolean isManagedWorldName(String worldName) {
        return settings.isManagedWorldName(worldName);
    }

    public World loadedWorld(int slot) {
        validateSlot(slot);
        return Bukkit.getWorld(settings.managedWorldName(slot));
    }

    public int loadedInstanceCount() {
        int loaded = 0;
        for (int slot = 1; slot <= settings.instanceCount(); slot++) {
            if (loadedWorld(slot) != null) loaded++;
        }
        return loaded;
    }

    public boolean canPrepare(int slot) {
        validateSlot(slot);
        World world = loadedWorld(slot);
        if (world != null && !world.getPlayers().isEmpty()) return false;
        World legacy = slot == 1 ? Bukkit.getWorld(settings.legacyManagedWorldName()) : null;
        return legacy == null || legacy.getPlayers().isEmpty();
    }

    public void validateForReset(int slot) {
        validateSlot(slot);
        requireEnabled();
        if (!canPrepare(slot)) throw new IllegalStateException("实例 " + slot + " 中仍有玩家");
        WorldManager manager = worldManager();
        String worldName = settings.managedWorldName(slot);
        MultiverseWorld known = manager.getWorld(worldName).getOrNull();
        if (known != null) assertManagedWorld(known);
        Path directory = ArenaWorldStorage.worldDirectory(settings, slot);
        if (Files.isDirectory(directory) && !Files.isRegularFile(directory.resolve(MARKER_FILE))) {
            throw new IllegalStateException("实例 " + slot + " 缺少 YknTD 所有权标记");
        }
        if (slot == 1 && !Files.exists(directory)) {
            MultiverseWorld legacy = manager.getWorld(settings.legacyManagedWorldName()).getOrNull();
            if (legacy != null) assertManagedWorld(legacy);
            Path legacyDirectory = ArenaWorldStorage.legacyWorldDirectory(settings);
            if (Files.isDirectory(legacyDirectory)
                    && !Files.isRegularFile(legacyDirectory.resolve(MARKER_FILE))) {
                throw new IllegalStateException("旧单世界缺少 YknTD 所有权标记");
            }
        }
    }

    public void enforceNight() {
        for (int slot = 1; slot <= settings.instanceCount(); slot++) {
            World world = loadedWorld(slot);
            if (world == null || !isDedicatedWorld(world)) continue;
            if (world.getTime() != 18000L) world.setTime(18000L);
            if (!Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            }
            enforceSpawnIsolation(world);
        }
    }

    public DedicatedArenaSettings settings() {
        return settings;
    }

    public Location fallbackLocation() {
        return Bukkit.getWorlds().stream()
                .filter(world -> !isDedicatedWorld(world))
                .map(World::getSpawnLocation)
                .findFirst()
                .orElse(null);
    }

    public void shutdown() {
        if (!MultiverseCoreApi.isLoaded()) return;
        for (int slot = 1; slot <= settings.instanceCount(); slot++) {
            shutdownWorld(settings.managedWorldName(slot));
        }
        shutdownWorld(settings.legacyManagedWorldName());
    }

    private void shutdownWorld(String worldName) {
        LoadedMultiverseWorld managed = worldManager().getLoadedWorld(worldName).getOrNull();
        if (managed == null) return;
        if (!generatorName().equalsIgnoreCase(managed.getGenerator())) {
            plugin.getLogger().warning("跳过卸载同名但不属于 YknTD 的世界：" + worldName);
            return;
        }
        World world = managed.getBukkitWorld().getOrNull();
        if (world != null && !world.getPlayers().isEmpty()) {
            Location fallback = fallbackLocation();
            if (fallback != null) {
                for (var player : List.copyOf(world.getPlayers())) player.teleport(fallback);
            }
            if (!world.getPlayers().isEmpty()) {
                plugin.getLogger().warning("夜袭实例仍有玩家，跳过卸载：" + worldName);
                return;
            }
        }
        var result = worldManager().unloadWorld(UnloadWorldOptions.world(managed)
                .saveBukkitWorld(true).unloadBukkitWorld(true));
        if (result.isFailure()) plugin.getLogger().warning("无法卸载夜袭实例 " + worldName + "：" + result.getFailureReason());
    }

    private LoadedMultiverseWorld createManagedWorld(WorldManager manager, String worldName) {
        var result = manager.createWorld(CreateWorldOptions.worldName(worldName)
                .environment(World.Environment.NORMAL)
                .worldType(WorldType.NORMAL)
                .seed(settings.seed())
                .generateStructures(false)
                .generator(generatorName())
                .useSpawnAdjust(false)
                .doFolderCheck(false));
        if (result.isFailure()) {
            throw new IllegalStateException("Multiverse 无法创建夜袭世界 " + worldName + "：" + result.getFailureReason());
        }
        LoadedMultiverseWorld world = result.getOrNull();
        if (world == null) throw new IllegalStateException("Multiverse 创建夜袭世界后未返回结果");
        plugin.getLogger().info("已创建专属浮岛夜袭实例 " + worldName);
        return world;
    }

    private World regenerate(WorldManager manager, LoadedMultiverseWorld managed, int slot) {
        var result = manager.regenWorld(RegenWorldOptions.world(managed)
                .seed(settings.seed())
                .randomSeed(false)
                .keepGameRule(false)
                .keepWorldBorder(false)
                .keepWorldConfig(true));
        if (result.isFailure()) {
            throw new IllegalStateException("Multiverse 重建夜袭实例 " + slot + " 失败：" + result.getFailureReason());
        }
        LoadedMultiverseWorld regenerated = result.getOrNull();
        World world = regenerated == null ? null : regenerated.getBukkitWorld().getOrNull();
        if (world == null) throw new IllegalStateException("Multiverse 重建实例 " + slot + " 后未加载世界");
        configureMultiverse(manager, regenerated);
        configureWorld(world);
        world.getChunkAt(0, 0).load(true);
        markGeneration(world, slot);
        return world;
    }

    private void configureMultiverse(WorldManager manager, MultiverseWorld world) {
        check(world.setAutoLoad(false), "关闭自动加载");
        check(world.setHidden(true), "隐藏世界");
        check(world.setPvp(false), "关闭 PVP");
        check(world.setAllowWeather(false), "关闭天气");
        check(world.setKeepSpawnInMemory(false), "关闭出生区常驻");
        var spawnConfig = world.getEntitySpawnConfig();
        for (SpawnCategory category : SpawnCategory.values()) {
            if (category == SpawnCategory.MISC) continue;
            check(spawnConfig.getSpawnCategoryConfig(category).setSpawn(false),
                    "关闭 " + category + " 自然生成");
        }
        spawnConfig.applyConfigToWorld();
        var saved = manager.saveWorldsConfig();
        if (saved.isFailure()) plugin.getLogger().warning("Multiverse 世界配置保存失败：" + saved.getCause());
    }

    private void configureWorld(World world) {
        DedicatedArenaLayout layout = new DedicatedArenaLayout(settings.floorY());
        ArenaDefinition definition = layout.createDefinition(settings.arenaId(), world);
        world.setSpawnLocation(definition.lobby());
        world.setTime(18000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        enforceSpawnIsolation(world);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(settings.radius() * 2.0);
        border.setWarningDistance(6);
    }

    private void enforceSpawnIsolation(World world) {
        if (!Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_MOB_SPAWNING))) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        }
        if (world.getAllowMonsters() || world.getAllowAnimals()) {
            world.setSpawnFlags(false, false);
        }
        for (SpawnCategory category : SpawnCategory.values()) {
            if (category != SpawnCategory.MISC && world.getTicksPerSpawns(category) != 0L) {
                world.setTicksPerSpawns(category, 0);
            }
        }
    }

    private void markGeneration(World world, int slot) {
        world.getPersistentDataContainer().set(
                generationKey, PersistentDataType.STRING, settings.generationFingerprint());
        writeMarker(slot);
        world.save();
    }

    private void ensureMarker(int slot) {
        Path marker = ArenaWorldStorage.worldDirectory(settings, slot).resolve(MARKER_FILE);
        if (Files.isRegularFile(marker)) return;
        writeMarker(slot);
    }

    private void writeMarker(int slot) {
        Path marker = ArenaWorldStorage.worldDirectory(settings, slot).resolve(MARKER_FILE);
        try {
            Files.writeString(marker, settings.generationFingerprint(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入夜袭世界所有权标记 " + marker, exception);
        }
    }

    private void assertManagedWorld(MultiverseWorld managed) {
        String generator = managed.getGenerator();
        if (!generatorName().equalsIgnoreCase(generator)) {
            throw new IllegalStateException("同名 Multiverse 世界不是 YknTD 竞技场，拒绝接管：" + managed.getName());
        }
    }

    private void migrateLegacySingleWorld(WorldManager manager) {
        String targetName = settings.managedWorldName(1);
        if (manager.isWorld(targetName) || Bukkit.getWorld(targetName) != null
                || Files.exists(ArenaWorldStorage.worldDirectory(settings, 1))) return;

        String legacyName = settings.legacyManagedWorldName();
        World legacyBukkit = Bukkit.getWorld(legacyName);
        MultiverseWorld legacyManaged = manager.getWorld(legacyName).getOrNull();
        if (legacyManaged != null) assertManagedWorld(legacyManaged);
        Path legacyFolder = legacyBukkit != null
                ? legacyBukkit.getWorldFolder().toPath().toAbsolutePath().normalize()
                : legacyManaged != null
                ? legacyManaged.getOfflineWorldFolder().toPath().toAbsolutePath().normalize()
                : ArenaWorldStorage.legacyWorldDirectory(settings);
        Path expectedLegacyFolder = ArenaWorldStorage.legacyWorldDirectory(settings);
        if (!legacyFolder.equals(expectedLegacyFolder)) {
            throw new IllegalStateException("旧夜袭世界目录不是预期路径，拒绝迁移：" + legacyFolder);
        }
        if (!Files.isDirectory(legacyFolder)) return;
        if (!Files.isRegularFile(legacyFolder.resolve(MARKER_FILE))) {
            throw new IllegalStateException("旧夜袭世界没有 YknTD 所有权标记，拒绝迁移：" + legacyFolder);
        }
        if (legacyBukkit != null && !legacyBukkit.getPlayers().isEmpty()) {
            throw new IllegalStateException("旧夜袭世界中仍有玩家，无法迁移到实例 01");
        }

        if (legacyManaged != null) {
            var removed = manager.removeWorld(RemoveWorldOptions.world(legacyManaged)
                    .saveBukkitWorld(true).unloadBukkitWorld(true));
            if (removed.isFailure()) {
                throw new IllegalStateException("Multiverse 无法注销旧夜袭世界：" + removed.getFailureReason());
            }
        } else if (legacyBukkit != null && !Bukkit.unloadWorld(legacyBukkit, true)) {
            throw new IllegalStateException("Bukkit 无法卸载旧夜袭世界 " + legacyName);
        }

        Path target = ArenaWorldStorage.worldDirectory(settings, 1);
        try {
            Files.createDirectories(target.getParent());
            Files.move(legacyFolder, target);
        } catch (IOException exception) {
            throw new IllegalStateException("无法把旧夜袭世界迁移为实例 01", exception);
        }
        plugin.getLogger().info("已把旧夜袭世界 " + legacyName + " 迁移到 " + targetName);
    }

    private void check(org.mvplugins.multiverse.external.vavr.control.Try<Void> result, String action) {
        if (result.isFailure()) plugin.getLogger().warning("Multiverse " + action + "失败：" + result.getCause());
    }

    private WorldManager worldManager() {
        if (!MultiverseCoreApi.isLoaded()) {
            throw new IllegalStateException("需要先安装并启用 Multiverse-Core 5.7.3+");
        }
        return MultiverseCoreApi.get().getWorldManager();
    }

    private String generatorName() {
        return plugin.getName() + ":" + GENERATOR_ID;
    }

    private void requireEnabled() {
        if (!settings.enabled()) throw new IllegalStateException("专属夜袭竞技场已在配置中禁用");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("世界操作必须在服务器主线程执行");
    }

    private void validateSlot(int slot) {
        if (slot < 1 || slot > settings.instanceCount()) {
            throw new IllegalArgumentException("竞技场实例编号必须在 1～" + settings.instanceCount() + " 之间");
        }
    }

    private boolean isExpectedWorldName(String worldName) {
        return settings.isManagedWorldName(worldName)
                || settings.legacyManagedWorldName().equalsIgnoreCase(worldName);
    }

    private record LoadResult(World world, boolean created) {
    }
}

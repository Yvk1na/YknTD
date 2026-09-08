package cn.ykntd;

import cn.ykntd.api.YknTdApi;
import cn.ykntd.arena.ArenaDefinition;
import cn.ykntd.arena.ArenaRepository;
import cn.ykntd.arena.DedicatedArenaLayout;
import cn.ykntd.arena.DedicatedArenaSettings;
import cn.ykntd.arena.DedicatedArenaWorldManager;
import cn.ykntd.arena.FloatingIslandArenaGenerator;
import cn.ykntd.command.YknTdCommand;
import cn.ykntd.config.GameSettings;
import cn.ykntd.config.WaveRepository;
import cn.ykntd.game.GameManager;
import cn.ykntd.integration.AuraSkillsPowerBridge;
import cn.ykntd.integration.MythicMobsBridge;
import cn.ykntd.integration.PartyPowerProvider;
import cn.ykntd.integration.RoseStackerBridge;
import cn.ykntd.listener.RaidListener;
import cn.ykntd.reward.RewardService;
import cn.ykntd.reward.PendingRewardRepository;
import cn.ykntd.stats.PlayerStatsRepository;
import cn.ykntd.stats.RecoveryRepository;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WaterMob;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class YknTdPlugin extends JavaPlugin {
    private NamespacedKey sessionKey;
    private NamespacedKey arenaKey;
    private NamespacedKey laneKey;
    private NamespacedKey combatDamageKey;
    private ArenaRepository arenas;
    private WaveRepository waves;
    private GameManager games;
    private DedicatedArenaSettings dedicatedArenaSettings;
    private DedicatedArenaWorldManager arenaWorlds;
    private BukkitTask arenaSetupTask;
    private BukkitTask nightGuardTask;

    @Override
    public void onEnable() {
        sessionKey = new NamespacedKey(this, "raid_session");
        arenaKey = new NamespacedKey(this, "raid_arena");
        laneKey = new NamespacedKey(this, "raid_lane");
        combatDamageKey = new NamespacedKey(this, "raid_combat_damage");
        saveDefaultConfig();
        ensureWaveResource();
        ensureResource("arenas.yml");
        cleanupLoadedOrphans();

        try {
            dedicatedArenaSettings = DedicatedArenaSettings.load(getConfig());
            arenaWorlds = new DedicatedArenaWorldManager(this, dedicatedArenaSettings);
            arenas = new ArenaRepository(this);
            waves = new WaveRepository(this);
            GameSettings settings = GameSettings.load(getConfig());
            MythicMobsBridge mythicMobs = new MythicMobsBridge(this);
            PartyPowerProvider partyPower = createPartyPowerProvider();
            games = new GameManager(
                    this,
                    arenas,
                    settings,
                    waves.all(),
                    mythicMobs,
                    partyPower,
                    new RewardService(this),
                    new PlayerStatsRepository(this),
                    new RecoveryRepository(this),
                    new PendingRewardRepository(this));

            getServer().getPluginManager().registerEvents(new RaidListener(this, games), this);
            boolean roseStackerGuard = RoseStackerBridge.register(this);
            PluginCommand command = getCommand("ykntd");
            if (command == null) throw new IllegalStateException("plugin.yml 缺少 ykntd 命令");
            YknTdCommand handler = new YknTdCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
            getServer().getServicesManager().register(YknTdApi.class, games, this, ServicePriority.Normal);

            if (dedicatedArenaSettings.enabled()) {
                nightGuardTask = getServer().getScheduler().runTaskTimer(
                        this, () -> arenaWorlds.enforceNight(), 20L, 20L);
                arenaSetupTask = getServer().getScheduler().runTask(this, () -> {
                    try {
                        ensureDedicatedArenaIdsAvailable();
                        evacuateUnownedDedicatedPlayers();
                        try {
                            arenaWorlds.migrateLegacyWorld();
                        } catch (RuntimeException exception) {
                            getLogger().warning("旧单世界暂未迁移，将隔离该槽并在后续重试：" + exception.getMessage());
                        }
                        arenaWorlds.reload(dedicatedArenaSettings);
                        if (dedicatedArenaSettings.autoCreate()) initializeDedicatedInstancesOnStartup();
                        else initializeLoadedDedicatedInstances(true);
                    } catch (RuntimeException exception) {
                        getLogger().severe("专属夜袭竞技场初始化失败：" + exception.getMessage());
                        exception.printStackTrace();
                        getServer().getPluginManager().disablePlugin(this);
                    }
                });
            }

            getLogger().info("YknTD " + getPluginMeta().getVersion() + " 已启用，读取 "
                    + waves.all().size() + " 个波次和 " + arenas.all().size() + " 个场地。"
                    + (roseStackerGuard ? " RoseStacker 防堆叠已启用。" : ""));
        } catch (RuntimeException exception) {
            getLogger().severe("YknTD 启用失败：" + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (arenaSetupTask != null) arenaSetupTask.cancel();
        if (nightGuardTask != null) nightGuardTask.cancel();
        if (games != null) games.shutdown();
        if (arenaWorlds != null) arenaWorlds.shutdown();
        getServer().getServicesManager().unregisterAll(this);
    }

    public ReloadResult reloadPluginData() {
        if (games != null && (!games.sessions().isEmpty() || games.hasDedicatedSessions())) {
            return ReloadResult.fail("仍有等待、重置、关闭或进行中的夜袭场次，必须先结束所有场次。");
        }
        DedicatedArenaSettings previousArenaSettings = dedicatedArenaSettings;
        try {
            reloadConfig();
            DedicatedArenaSettings reloadedArenaSettings = DedicatedArenaSettings.load(getConfig());
            if (previousArenaSettings.enabled() != reloadedArenaSettings.enabled()) {
                return ReloadResult.fail("dedicated-arena.enabled 的修改需要完整重启服务器。");
            }
            if (!previousArenaSettings.sameGeneratedWorld(reloadedArenaSettings)) {
                return ReloadResult.fail("arena-id、world-name、instance-count、floor-y 或 seed 的修改需要完整重启服务器；本次未切换世界。");
            }
            arenas.reload();
            dedicatedArenaSettings = reloadedArenaSettings;
            arenaWorlds.reload(reloadedArenaSettings);
            if (reloadedArenaSettings.enabled()) {
                ensureDedicatedArenaIdsAvailable();
                if (reloadedArenaSettings.autoCreate()) initializeDedicatedInstances(false);
                else initializeLoadedDedicatedInstances(false);
            }
            waves.reload();
            games.reload(GameSettings.load(getConfig()), waves.all());
            return ReloadResult.ok("已重载 " + waves.all().size() + " 个波次和 " + arenas.all().size() + " 个场地。");
        } catch (RuntimeException exception) {
            dedicatedArenaSettings = previousArenaSettings;
            if (arenaWorlds != null && previousArenaSettings != null) {
                try {
                    arenaWorlds.reload(previousArenaSettings);
                    ensureDedicatedArenaIdsAvailable();
                    if (previousArenaSettings.autoCreate()) initializeDedicatedInstances(false);
                    else initializeLoadedDedicatedInstances(false);
                } catch (RuntimeException rollbackFailure) {
                    getLogger().severe("恢复重载前竞技场状态失败：" + rollbackFailure.getMessage());
                }
            }
            getLogger().severe("重载失败：" + exception.getMessage());
            return ReloadResult.fail("重载失败：" + exception.getMessage());
        }
    }

    public ArenaRepository arenas() {
        return arenas;
    }

    public GameManager games() {
        return games;
    }

    public DedicatedArenaWorldManager arenaWorlds() {
        return arenaWorlds;
    }

    public ArenaOperationResult generateDedicatedArena(boolean regenerate) {
        if (games != null && games.hasDedicatedSessions()) {
            return ArenaOperationResult.fail("仍有专属实例处于等待、重置或进行中，必须先停止。");
        }
        try {
            ensureDedicatedArenaIdsAvailable();
            initializeDedicatedInstances(regenerate);
            return ArenaOperationResult.ok((regenerate ? "已重置" : "已生成并加载") + "全部 "
                    + dedicatedArenaSettings.instanceCount() + " 个专属竞技场实例。");
        } catch (RuntimeException exception) {
            getLogger().warning("专属竞技场操作失败：" + exception.getMessage());
            return ArenaOperationResult.fail(exception.getMessage());
        }
    }

    public ArenaPreparationResult prepareDedicatedArenaForSession(int slot) {
        if (dedicatedArenaSettings == null || !dedicatedArenaSettings.enabled()) {
            return ArenaPreparationResult.fail("专属夜袭竞技场已禁用");
        }
        if (!getServer().isPrimaryThread()) {
            return ArenaPreparationResult.fail("竞技场只能在服务器主线程重置");
        }
        try {
            ensureDedicatedArenaIdsAvailable();
            var world = dedicatedArenaSettings.resetBeforeSession()
                    ? arenaWorlds.regenerate(slot) : arenaWorlds.createOrLoad(slot);
            ArenaDefinition definition = new DedicatedArenaLayout(dedicatedArenaSettings.floorY())
                    .createDefinition(dedicatedArenaSettings.arenaId(), world);
            if (slot == 1) arenas.installGenerated(definition);
            cleanupWorldOrphans(world);
            getLogger().info("专属竞技场实例 " + slot + "/" + dedicatedArenaSettings.instanceCount()
                    + " 已在入场前就绪：" + world.getName());
            return ArenaPreparationResult.ok(definition, slot);
        } catch (RuntimeException exception) {
            getLogger().warning("实例 " + slot + " 入场前重置失败：" + exception.getMessage());
            try {
                var recovered = arenaWorlds.createOrLoad(slot);
                cleanupWorldOrphans(recovered);
            } catch (RuntimeException recoveryFailure) {
                exception.addSuppressed(recoveryFailure);
                getLogger().severe("实例恢复也失败：" + recoveryFailure.getMessage());
            }
            return ArenaPreparationResult.fail("实例 " + slot + " 入场前重置失败：" + exception.getMessage());
        }
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (dedicatedArenaSettings == null || !dedicatedArenaSettings.enabled()) return null;
        boolean matchingId = DedicatedArenaWorldManager.GENERATOR_ID.equalsIgnoreCase(id);
        boolean matchingWorld = (id == null || id.isBlank())
                && (dedicatedArenaSettings.isManagedWorldName(worldName)
                || dedicatedArenaSettings.legacyManagedWorldName().equalsIgnoreCase(worldName));
        return matchingId || matchingWorld
                ? new FloatingIslandArenaGenerator(dedicatedArenaSettings.floorY()) : null;
    }

    public NamespacedKey sessionKey() {
        return sessionKey;
    }

    public NamespacedKey arenaKey() {
        return arenaKey;
    }

    public NamespacedKey laneKey() {
        return laneKey;
    }

    public NamespacedKey combatDamageKey() {
        return combatDamageKey;
    }

    private void ensureResource(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.isFile()) saveResource(name, false);
    }

    private void ensureWaveResource() {
        File target = new File(getDataFolder(), "waves.yml");
        if (!target.isFile()) {
            saveResource("waves.yml", false);
            return;
        }
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
        if (existing.getInt("content-version", 0) >= 3) return;
        File backup = new File(getDataFolder(), "waves.pre-1.4.0.yml");
        try {
            if (!backup.exists()) {
                Files.copy(target.toPath(), backup.toPath(),
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            saveResource("waves.yml", true);
            getLogger().warning("旧版波次已备份到 waves.pre-1.4.0.yml，并迁移为新版 AuraSkills 平衡配置。");
        } catch (IOException exception) {
            throw new IllegalStateException("无法备份旧 waves.yml：" + exception.getMessage(), exception);
        }
    }

    private void cleanupLoadedOrphans() {
        int removed = 0;
        for (var world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(sessionKey, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) getLogger().info("已清理 " + removed + " 个上次夜袭遗留实体。");
    }

    private void installDedicatedArenaIndex(org.bukkit.World world) {
        arenas.installGenerated(new DedicatedArenaLayout(dedicatedArenaSettings.floorY())
                .createDefinition(dedicatedArenaSettings.arenaId(), world));
        cleanupWorldOrphans(world);
        getLogger().info("专属竞技场入口已就绪：" + dedicatedArenaSettings.arenaId()
                + " -> " + world.getName());
    }

    private void ensureDedicatedArenaIdsAvailable() {
        if (!arenas.canInstallGenerated(dedicatedArenaSettings.arenaId())) {
            throw new IllegalStateException("专属场地 ID " + dedicatedArenaSettings.arenaId()
                    + " 已被手工场地使用；请修改 config.yml 中的 dedicated-arena.arena-id");
        }
        for (int slot = 1; slot <= dedicatedArenaSettings.instanceCount(); slot++) {
            String instanceId = dedicatedArenaSettings.instanceArenaId(slot);
            if (!arenas.canInstallGenerated(instanceId)) {
                throw new IllegalStateException("实例 ID " + instanceId
                        + " 已被手工场地使用；请修改 dedicated-arena.arena-id");
            }
        }
    }

    private void initializeDedicatedInstances(boolean regenerate) {
        if (regenerate) {
            for (int slot = 1; slot <= dedicatedArenaSettings.instanceCount(); slot++) {
                if (games != null && games.isDedicatedSlotInUse(slot)) {
                    throw new IllegalStateException("实例 " + slot + " 仍在使用或有玩家滞留，未执行任何重置");
                }
                arenaWorlds.validateForReset(slot);
            }
        }
        org.bukkit.World first = null;
        for (int slot = 1; slot <= dedicatedArenaSettings.instanceCount(); slot++) {
            if (games != null && games.isDedicatedSlotInUse(slot)) continue;
            org.bukkit.World world = regenerate ? arenaWorlds.regenerate(slot) : arenaWorlds.createOrLoad(slot);
            cleanupWorldOrphans(world);
            if (slot == 1) first = world;
        }
        if (first != null) installDedicatedArenaIndex(first);
    }

    private void initializeDedicatedInstancesOnStartup() {
        org.bukkit.World first = null;
        int ready = 0;
        for (int slot = 1; slot <= dedicatedArenaSettings.instanceCount(); slot++) {
            if (games != null && games.isDedicatedSlotInUse(slot)) continue;
            try {
                org.bukkit.World world = arenaWorlds.createOrLoad(slot);
                cleanupWorldOrphans(world);
                if (slot == 1) first = world;
                ready++;
            } catch (RuntimeException exception) {
                getLogger().severe("启动时跳过不可用的夜袭实例 " + slot + "：" + exception.getMessage());
            }
        }
        if (first != null) installDedicatedArenaIndex(first);
        if (ready == 0) throw new IllegalStateException("没有任何专属夜袭实例能够创建或加载");
    }

    private void initializeLoadedDedicatedInstances(boolean tolerateFailures) {
        org.bukkit.World first = null;
        for (int slot = 1; slot <= dedicatedArenaSettings.instanceCount(); slot++) {
            if (games != null && games.isDedicatedSlotInUse(slot)) continue;
            if (arenaWorlds.loadedWorld(slot) == null) continue;
            try {
                org.bukkit.World world = arenaWorlds.createOrLoad(slot);
                cleanupWorldOrphans(world);
                if (slot == 1) first = world;
            } catch (RuntimeException exception) {
                if (!tolerateFailures) throw exception;
                getLogger().severe("已隔离不可用的已加载夜袭实例 " + slot + "：" + exception.getMessage());
            }
        }
        if (first != null) installDedicatedArenaIndex(first);
    }

    private void evacuateUnownedDedicatedPlayers() {
        org.bukkit.Location fallback = arenaWorlds.fallbackLocation();
        if (fallback == null) return;
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            if (!arenaWorlds.isDedicatedWorld(player.getWorld())) continue;
            var session = games.session(player).orElse(null);
            if (session != null && player.getWorld().equals(session.arena().core().getWorld())) continue;
            if (!player.teleport(fallback)) {
                getLogger().warning("无法把无主夜袭实例中的玩家撤离：" + player.getName());
            }
        }
    }

    private void cleanupWorldOrphans(org.bukkit.World world) {
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            String tag = entity.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
            boolean staleRaidEntity = tag != null && (games == null || !games.ownsSessionTag(tag));
            boolean strayPassiveCreature = tag == null
                    && (entity instanceof Animals || entity instanceof Ambient || entity instanceof WaterMob);
            if (staleRaidEntity || strayPassiveCreature) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) getLogger().info("已清理专属世界中的 " + removed + " 个遗留实体。");
    }

    private PartyPowerProvider createPartyPowerProvider() {
        if (!getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            getLogger().warning("未检测到 AuraSkills，队伍战力修正将使用 1.0。");
            return PartyPowerProvider.unavailable();
        }
        try {
            PartyPowerProvider provider = new AuraSkillsPowerBridge();
            getLogger().info("AuraSkills 队伍战力快照已接入。");
            return provider;
        } catch (RuntimeException | LinkageError exception) {
            getLogger().warning("AuraSkills 战力接口不可用，回退到 1.0：" + exception.getMessage());
            return PartyPowerProvider.unavailable();
        }
    }

    public record ReloadResult(boolean success, String message) {
        public static ReloadResult ok(String message) {
            return new ReloadResult(true, message);
        }

        public static ReloadResult fail(String message) {
            return new ReloadResult(false, message);
        }
    }

    public record ArenaOperationResult(boolean success, String message) {
        public static ArenaOperationResult ok(String message) {
            return new ArenaOperationResult(true, message);
        }

        public static ArenaOperationResult fail(String message) {
            return new ArenaOperationResult(false, message);
        }
    }

    public record ArenaPreparationResult(boolean success, String message,
                                         ArenaDefinition arena, int slot) {
        public static ArenaPreparationResult ok(ArenaDefinition arena, int slot) {
            return new ArenaPreparationResult(true, "实例已就绪", arena, slot);
        }

        public static ArenaPreparationResult fail(String message) {
            return new ArenaPreparationResult(false, message, null, 0);
        }
    }
}

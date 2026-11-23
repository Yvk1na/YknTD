package cn.ykntd.game;

import cn.ykntd.YknTdPlugin;
import cn.ykntd.api.ContributionSnapshot;
import cn.ykntd.api.YknTdApi;
import cn.ykntd.arena.ArenaDefinition;
import cn.ykntd.arena.ArenaRepository;
import cn.ykntd.arena.DedicatedArenaPool;
import cn.ykntd.arena.DedicatedArenaSettings;
import cn.ykntd.config.GameSettings;
import cn.ykntd.integration.MythicMobsBridge;
import cn.ykntd.integration.PartyPowerProvider;
import cn.ykntd.model.Contribution;
import cn.ykntd.model.ContributionType;
import cn.ykntd.model.RaidState;
import cn.ykntd.model.WaveDefinition;
import cn.ykntd.reward.RewardService;
import cn.ykntd.reward.PendingRewardRepository;
import cn.ykntd.stats.PlayerStatsRepository;
import cn.ykntd.stats.RecoveryRepository;
import dev.chengzhi.skyblockcore.profile.ProfileIdentity;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import cn.ykntd.util.Messages;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class GameManager implements YknTdApi {
    private final YknTdPlugin plugin;
    private final ArenaRepository arenas;
    private final MythicMobsBridge mythicMobs;
    private final PartyPowerProvider partyPower;
    private final RewardService rewards;
    private final PlayerStatsRepository stats;
    private final RecoveryRepository recovery;
    private final PendingRewardRepository pendingRewards;
    private final DedicatedArenaPool<RaidSession> dedicatedPool;
    private final Map<String, RaidSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, RaidSession> participants = new HashMap<>();
    private final Map<UUID, RaidSession> enemies = new HashMap<>();
    private GameSettings settings;
    private List<WaveDefinition> waves;
    private boolean shuttingDown;

    public GameManager(YknTdPlugin plugin, ArenaRepository arenas,
                       GameSettings settings, List<WaveDefinition> waves,
                       MythicMobsBridge mythicMobs, PartyPowerProvider partyPower,
                       RewardService rewards,
                       PlayerStatsRepository stats, RecoveryRepository recovery,
                       PendingRewardRepository pendingRewards) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.settings = settings;
        this.waves = List.copyOf(waves);
        this.mythicMobs = mythicMobs;
        this.partyPower = partyPower;
        this.rewards = rewards;
        this.stats = stats;
        this.recovery = recovery;
        this.pendingRewards = pendingRewards;
        dedicatedPool = new DedicatedArenaPool<>(plugin.arenaWorlds().settings().instanceCount());
    }

    public ActionResult join(Player player, String arenaId) {
        if (!Bukkit.isPrimaryThread()) {
            return ActionResult.fail("加入夜袭只能在服务器主线程执行。");
        }
        if (participants.containsKey(player.getUniqueId())) {
            return ActionResult.fail("你已经在一场浮岛夜袭中。");
        }
        String normalizedArenaId = ArenaRepository.normalize(arenaId);
        DedicatedArenaSettings dedicated = plugin.arenaWorlds().settings();
        if (dedicated.enabled() && dedicated.arenaId().equals(normalizedArenaId)) {
            return joinDedicated(player);
        }
        if (dedicated.enabled() && dedicated.isInternalInstanceId(normalizedArenaId)) {
            return ActionResult.fail("不能指定内部场次，请使用场地 ID " + dedicated.arenaId()
                    + "，系统会自动分配空闲竞技场。");
        }

        ArenaDefinition arena = arenas.find(normalizedArenaId).orElse(null);
        if (arena == null) return ActionResult.fail("不存在场地：" + normalizedArenaId);
        List<String> problems = arena.problems(settings.boundaryRadius());
        if (!problems.isEmpty()) return ActionResult.fail("场地尚未配置完成：" + String.join("、", problems));
        RaidSession session = sessions.computeIfAbsent(arena.id(), ignored ->
                new RaidSession(plugin, this, arena.id(), arena, waves, settings, mythicMobs,
                        partyPower, rewards, stats));
        return joinSession(player, session);
    }

    private ActionResult joinDedicated(Player player) {
        for (DedicatedArenaPool.Slot<RaidSession> slot : dedicatedPool.activeSlots()) {
            if (slot.value().isJoinable()) {
                return joinSession(player, slot.value());
            }
        }

        return createDedicated(player);
    }

    private ActionResult createDedicated(Player player) {
        DedicatedArenaSettings dedicated = plugin.arenaWorlds().settings();
        java.util.Set<Integer> attempted = new HashSet<>();
        while (attempted.size() < dedicated.instanceCount()) {
            var reserved = dedicatedPool.reserveFirstAvailable(slot ->
                    !attempted.contains(slot) && plugin.arenaWorlds().canPrepare(slot));
            if (reserved.isEmpty()) break;
            int slot = reserved.getAsInt();
            attempted.add(slot);
            RaidSession session = null;
            boolean activated = false;
            try {
                YknTdPlugin.ArenaPreparationResult preparation = plugin.prepareDedicatedArenaForSession(slot);
                if (!preparation.success()) {
                    plugin.getLogger().warning("专属夜袭竞技场准备失败：" + preparation.message());
                    continue;
                }
                ArenaDefinition arena = preparation.arena();
                List<String> problems = arena.problems(settings.boundaryRadius());
                if (!problems.isEmpty()) {
                    plugin.getLogger().warning("专属夜袭实例 " + slot + " 尚未配置完成："
                            + String.join("、", problems));
                    continue;
                }
                String instanceId = dedicated.instanceArenaId(slot);
                session = new RaidSession(plugin, this, instanceId, arena, waves, settings, mythicMobs,
                        partyPower, rewards, stats);
                if (!dedicatedPool.activate(slot, session)) {
                    session.stop("实例占用状态异常");
                    plugin.getLogger().warning("专属夜袭实例 " + slot + " 占用失败");
                    continue;
                }
                activated = true;
                sessions.put(instanceId, session);
                return joinSession(player, session);
            } catch (RuntimeException exception) {
                if (session != null) session.stop("实例创建失败");
                plugin.getLogger().warning("创建专属夜袭实例 " + slot + " 失败：" + exception.getMessage());
            } finally {
                if (!activated) dedicatedPool.cancelReservation(slot);
            }
        }
        return ActionResult.fail("所有夜袭竞技场正在使用、存在玩家滞留或暂时不可用，请稍后再试。");
    }

    private ActionResult joinSession(Player player, RaidSession session) {
        if (!session.isJoinable()) return ActionResult.fail("该场夜袭已经开始或人数已满。");
        participants.put(player.getUniqueId(), session);
        boolean added;
        try {
            added = session.addPlayer(player);
        } catch (RuntimeException exception) {
            if (session.isParticipant(player.getUniqueId())) {
                session.removePlayer(player, true, "加入过程发生异常");
            } else {
                participants.remove(player.getUniqueId(), session);
                if (session.participantCount() == 0) session.stop("加入过程发生异常，场次已取消");
            }
            plugin.getLogger().warning("玩家 " + player.getName() + " 加入 " + session.sessionKey()
                    + " 时发生异常：" + exception.getMessage());
            return ActionResult.fail("加入竞技场时发生异常，请重试。");
        }
        if (!added) {
            participants.remove(player.getUniqueId(), session);
            if (session.participantCount() == 0) session.stop("传送失败，场次已取消");
            return ActionResult.fail("无法传送到场地，加入已取消。");
        }
        return ActionResult.ok("已加入浮岛夜袭");
    }

    public ActionResult leave(Player player) {
        RaidSession session = participants.get(player.getUniqueId());
        if (session == null) return ActionResult.fail("你当前没有参加浮岛夜袭。");
        session.removePlayer(player, true, "");
        return ActionResult.ok("已离开夜袭");
    }

    public ActionResult start(String arenaId) {
        SessionResolution resolution = resolveSession(arenaId);
        if (resolution.error() != null) return ActionResult.fail(resolution.error());
        RaidSession session = resolution.session();
        if (session == null) return ActionResult.fail("该场地还没有玩家等待。");
        if (!session.startCountdown(true)) {
            return ActionResult.fail("无法开始：需要处于等待状态且至少有 " + settings.minPlayers() + " 名玩家。");
        }
        return ActionResult.ok("开始倒计时");
    }

    public ActionResult stop(String arenaId, String reason) {
        SessionResolution resolution = resolveSession(arenaId);
        if (resolution.error() != null) return ActionResult.fail(resolution.error());
        RaidSession session = resolution.session();
        if (session == null) return ActionResult.fail("该场地没有正在进行的夜袭。");
        session.stop(reason);
        return ActionResult.ok("已终止夜袭");
    }

    public Optional<RaidSession> session(String arenaId) {
        SessionResolution resolution = resolveSession(arenaId);
        return resolution.error() == null ? Optional.ofNullable(resolution.session()) : Optional.empty();
    }

    public Optional<RaidSession> session(Player player) {
        return Optional.ofNullable(participants.get(player.getUniqueId()));
    }

    public Optional<RaidSession> sessionByEnemy(Entity entity) {
        if (entity == null) return Optional.empty();
        RaidSession direct = enemies.get(entity.getUniqueId());
        if (direct != null) return Optional.of(direct);
        String tag = entity.getPersistentDataContainer().get(
                plugin.sessionKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (tag == null) return Optional.empty();
        return sessions.values().stream().filter(session -> session.sessionTag().equals(tag)).findFirst();
    }

    public Optional<RaidSession> activeAt(Location location) {
        return sessions.values().stream()
                .filter(session -> session.state() != RaidState.WAITING && !session.state().terminal())
                .filter(session -> session.contains(location))
                .findFirst();
    }

    public Collection<RaidSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public List<String> sessionKeys() {
        return List.copyOf(sessions.keySet());
    }

    public List<String> dedicatedSessionKeys() {
        DedicatedArenaSettings dedicated = plugin.arenaWorlds().settings();
        return dedicatedPool.activeSlots().stream()
                .map(slot -> dedicated.instanceArenaId(slot.number()))
                .toList();
    }

    public Optional<RaidSession> sessionAt(org.bukkit.World world) {
        if (world == null) return Optional.empty();
        return sessions.values().stream()
                .filter(session -> world.equals(session.arena().core().getWorld()))
                .findFirst();
    }

    public boolean hasSession(String arenaId) {
        String normalized = ArenaRepository.normalize(arenaId);
        if (sessions.containsKey(normalized)) return true;
        return plugin.arenaWorlds().settings().arenaId().equals(normalized)
                && dedicatedPool.activeCount() > 0;
    }

    public boolean hasDedicatedSessions() {
        return dedicatedPool.activeCount() > 0 || dedicatedPool.reservedCount() > 0;
    }

    public boolean isDedicatedSlotInUse(int slot) {
        return dedicatedPool.inUse(slot);
    }

    public int activeDedicatedCount() {
        return dedicatedPool.activeCount();
    }

    public boolean isProtectedArena(Location location) {
        if (!settings.protectArena() || location == null || location.getWorld() == null) return false;
        if (plugin.arenaWorlds() != null && plugin.arenaWorlds().isDedicatedWorld(location.getWorld())) return true;
        return activeAt(location).isPresent();
    }

    void evacuateFromDedicatedWorld(Player player) {
        if (plugin.arenaWorlds() == null || !plugin.arenaWorlds().isDedicatedWorld(player.getWorld())) return;
        Location fallback = plugin.arenaWorlds().fallbackLocation();
        if (fallback != null) player.teleport(fallback);
    }

    public boolean ownsSessionTag(String tag) {
        return tag != null && sessions.values().stream().anyMatch(session -> session.sessionTag().equals(tag));
    }

    public boolean hasParticipants() {
        return !participants.isEmpty();
    }

    public GameSettings settings() {
        return settings;
    }

    public PlayerStatsRepository stats() {
        return stats;
    }

    public UUID activeCharacter(UUID accountId) {
        return plugin.getServer().getPluginManager().isPluginEnabled("Skyblock-Core")
                ? ProfileIdentity.characterId(plugin.getServer(), accountId) : accountId;
    }

    void rememberReturn(UUID playerId, Location location) {
        recovery.remember(playerId, location);
    }

    void clearReturn(UUID playerId) {
        recovery.clear(playerId);
    }

    public boolean restorePending(Player player) {
        UUID character = activeCharacter(player.getUniqueId());
        Location location = recovery.get(character).orElse(null);
        if (location == null || location.getWorld() == null) return false;
        if (!player.teleport(location)) return false;
        recovery.clear(character);
        return true;
    }

    void queuePendingReward(UUID playerId, UUID characterId, String playerName, String arena, boolean victory,
                            int wave, Contribution contribution, int participantCount) {
        RewardService.RewardPlan plan = rewards.prepare(
                playerId, playerName, arena, victory, wave, contribution,
                settings.contributionWeights(), settings.minimumRewardScore(), participantCount);
        pendingRewards.put(characterId, plan);
    }

    public boolean deliverPendingReward(Player player) {
        RewardService.RewardPlan plan = pendingRewards.take(activeCharacter(player.getUniqueId())).orElse(null);
        if (plan == null) return false;
        RewardService.RewardResult result = rewards.execute(plan);
        Messages.send(plugin, player, "pending-reward",
                "&a已补发离线期间结算的夜袭奖励。 &7贡献 {score}，档位 {tier}。",
                Map.of("score", new java.text.DecimalFormat("0.##").format(result.score()),
                        "tier", result.tierName()));
        return true;
    }

    public void reload(GameSettings newSettings, List<WaveDefinition> newWaves) {
        if (!sessions.isEmpty() || dedicatedPool.reservedCount() > 0) {
            throw new IllegalStateException("仍有等待、重置或进行中的夜袭场次");
        }
        settings = newSettings;
        waves = List.copyOf(newWaves);
    }

    public void shutdown() {
        shuttingDown = true;
        for (RaidSession session : List.copyOf(sessions.values())) {
            session.stop("插件正在关闭");
        }
        sessions.clear();
        dedicatedPool.clear();
        participants.clear();
        enemies.clear();
    }

    void registerEnemy(UUID entityId, RaidSession session) {
        enemies.put(entityId, session);
    }

    void unregisterEnemy(UUID entityId, RaidSession session) {
        enemies.remove(entityId, session);
    }

    void unregisterParticipant(UUID playerId, RaidSession session) {
        participants.remove(playerId, session);
    }

    void sessionEnded(RaidSession session) {
        for (UUID id : session.participantIds()) participants.remove(id, session);
        enemies.entrySet().removeIf(entry -> entry.getValue() == session);
        OptionalInt slot = plugin.arenaWorlds().settings().instanceSlot(session.sessionKey());
        if (slot.isEmpty()) {
            sessions.remove(session.sessionKey(), session);
            return;
        }
        if (shuttingDown) {
            finishDedicatedClose(slot.getAsInt(), session);
            return;
        }
        closeDedicatedSlot(slot.getAsInt(), session);
    }

    private void closeDedicatedSlot(int slot, RaidSession session) {
        org.bukkit.World world = session.arena().core().getWorld();
        if (world == null || world.getPlayers().isEmpty()) {
            finishDedicatedClose(slot, session);
            return;
        }
        new BukkitRunnable() {
            private int attempts;

            @Override
            public void run() {
                if (dedicatedPool.active(slot).orElse(null) != session) {
                    cancel();
                    return;
                }
                org.bukkit.World current = Bukkit.getWorld(world.getUID());
                if (current == null || current.getPlayers().isEmpty()) {
                    finishDedicatedClose(slot, session);
                    cancel();
                    return;
                }
                for (Player player : List.copyOf(current.getPlayers())) {
                    if (player.isDead()) player.spigot().respawn();
                    else evacuateFromDedicatedWorld(player);
                }
                if (current.getPlayers().isEmpty()) {
                    finishDedicatedClose(slot, session);
                    cancel();
                } else if (++attempts % 60 == 0) {
                    plugin.getLogger().warning("夜袭实例 " + slot
                            + " 仍有玩家，保持关闭占用状态并继续尝试撤离");
                }
            }
        }.runTaskTimer(plugin, 1L, 10L);
    }

    private void finishDedicatedClose(int slot, RaidSession session) {
        sessions.remove(session.sessionKey(), session);
        dedicatedPool.release(slot, session);
    }

    @Override
    public boolean isParticipant(UUID playerId) {
        return participants.containsKey(playerId);
    }

    @Override
    public Optional<String> arenaOf(UUID playerId) {
        return Optional.ofNullable(participants.get(playerId)).map(RaidSession::arenaId);
    }

    @Override
    public boolean addContribution(UUID playerId, ContributionType type, double amount) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("拒绝来自异步线程的 YknTdApi#addContribution 调用");
            return false;
        }
        RaidSession session = participants.get(playerId);
        return session != null && session.addContribution(playerId, type, amount);
    }

    @Override
    public Optional<ContributionSnapshot> contributionOf(UUID playerId) {
        RaidSession session = participants.get(playerId);
        if (session == null) return Optional.empty();
        Contribution contribution = session.contribution(playerId).orElse(null);
        if (contribution == null) return Optional.empty();
        return Optional.of(new ContributionSnapshot(contribution.snapshot(),
                contribution.score(settings.contributionWeights())));
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }
    }

    private SessionResolution resolveSession(String rawId) {
        String normalized = ArenaRepository.normalize(rawId);
        RaidSession exact = sessions.get(normalized);
        if (exact != null) return new SessionResolution(exact, null);
        if (!plugin.arenaWorlds().settings().arenaId().equals(normalized)) {
            return new SessionResolution(null, null);
        }
        List<DedicatedArenaPool.Slot<RaidSession>> active = dedicatedPool.activeSlots();
        if (active.size() == 1) return new SessionResolution(active.getFirst().value(), null);
        if (active.size() > 1) {
            return new SessionResolution(null, "当前有多个夜袭实例，请指定："
                    + String.join("、", dedicatedSessionKeys()));
        }
        return new SessionResolution(null, null);
    }

    private record SessionResolution(RaidSession session, String error) {
    }
}

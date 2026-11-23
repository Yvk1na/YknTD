package cn.ykntd.game;

import cn.ykntd.YknTdPlugin;
import cn.ykntd.arena.ArenaDefinition;
import cn.ykntd.config.GameSettings;
import cn.ykntd.hud.RaidSidebar;
import cn.ykntd.hud.RaidSidebarSettings;
import cn.ykntd.hud.RaidSidebarSnapshot;
import cn.ykntd.integration.MythicMobsBridge;
import cn.ykntd.integration.PartyPowerProvider;
import cn.ykntd.model.Contribution;
import cn.ykntd.model.ContributionType;
import cn.ykntd.model.Lane;
import cn.ykntd.model.MobGroup;
import cn.ykntd.model.MobProvider;
import cn.ykntd.model.PartyPowerProfile;
import cn.ykntd.model.RaidState;
import cn.ykntd.model.WaveDefinition;
import cn.ykntd.reward.RewardService;
import cn.ykntd.stats.PlayerStatsRepository;
import cn.ykntd.util.Messages;
import cn.ykntd.util.PlayerMessagePolicy;
import cn.ykntd.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

public final class RaidSession {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");
    private static final long STUCK_TICKS = 200L;

    private final YknTdPlugin plugin;
    private final GameManager manager;
    private final String sessionKey;
    private final ArenaDefinition arena;
    private final List<WaveDefinition> waves;
    private final GameSettings settings;
    private final MythicMobsBridge mythicMobs;
    private final PartyPowerProvider partyPowerProvider;
    private final RewardService rewards;
    private final PlayerStatsRepository stats;
    private final RaidSidebar sidebar;
    private final UUID sessionId = UUID.randomUUID();
    private final LinkedHashSet<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, String> participantNames = new HashMap<>();
    private final Map<UUID, UUID> participantCharacters = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, Contribution> contributions = new LinkedHashMap<>();
    private final Map<UUID, Long> repairCooldowns = new HashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();
    private final Map<UUID, RaidEnemy> enemies = new LinkedHashMap<>();
    private final ArrayDeque<SpawnRequest> spawnQueue = new ArrayDeque<>();
    private final EnumMap<Lane, Double> wardHealth = new EnumMap<>(Lane.class);
    private final EnumSet<Lane> breached = EnumSet.noneOf(Lane.class);
    private final EnumMap<Lane, TextDisplay> wardDisplays = new EnumMap<>(Lane.class);
    private final Set<ChunkRef> chunkTickets = new LinkedHashSet<>();
    private final Set<UUID> projectiles = new LinkedHashSet<>();
    private final BossBar bossBar;
    private RaidState state = RaidState.WAITING;
    private BukkitTask task;
    private TextDisplay coreDisplay;
    private int waveIndex = -1;
    private int scalingPlayers = 1;
    private PartyPowerProfile partyPower = PartyPowerProfile.unavailable();
    private double powerHealthMultiplier = 1.0;
    private double powerDamageMultiplier = 1.0;
    private int phaseTicks;
    private int waveTicks;
    private int lastAnnouncedSecond = Integer.MIN_VALUE;
    private long clockTicks;
    private long nextSpawnTick;
    private double coreHealth;
    private boolean settled;
    private int consecutiveSpawnFailures;
    private long allOfflineSince = -1L;

    RaidSession(YknTdPlugin plugin, GameManager manager, String sessionKey, ArenaDefinition arena,
                List<WaveDefinition> waves, GameSettings settings,
                MythicMobsBridge mythicMobs, PartyPowerProvider partyPowerProvider,
                RewardService rewards,
                PlayerStatsRepository stats) {
        this.plugin = plugin;
        this.manager = manager;
        this.sessionKey = sessionKey;
        this.arena = arena;
        this.waves = List.copyOf(waves);
        this.settings = settings;
        this.mythicMobs = mythicMobs;
        this.partyPowerProvider = partyPowerProvider;
        this.rewards = rewards;
        this.stats = stats;
        this.sidebar = new RaidSidebar(plugin, RaidSidebarSettings.load(plugin.getConfig()));
        coreHealth = settings.coreMaxHealth();
        for (Lane lane : Lane.values()) wardHealth.put(lane, settings.wardMaxHealth());
        bossBar = BossBar.bossBar(Text.component("&c浮岛核心"), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tick, 1L, settings.tickPeriodTicks());
    }

    public String arenaId() {
        return arena.id();
    }

    public String sessionKey() {
        return sessionKey;
    }

    public String sessionTag() {
        return sessionId.toString();
    }

    public ArenaDefinition arena() {
        return arena;
    }

    public RaidState state() {
        return state;
    }

    public int waveNumber() {
        return Math.max(0, waveIndex + 1);
    }

    public int totalWaves() {
        return waves.size();
    }

    public int enemyCount() {
        return enemies.size() + spawnQueue.size();
    }

    public double coreHealth() {
        return coreHealth;
    }

    public double wardHealth(Lane lane) {
        return wardHealth.getOrDefault(lane, 0.0);
    }

    public boolean breached(Lane lane) {
        return breached.contains(lane);
    }

    public Collection<UUID> participantIds() {
        return List.copyOf(participants);
    }

    public int participantCount() {
        return participants.size();
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public OptionalDouble combatDamage(Entity damager) {
        if (damager instanceof Projectile projectile) {
            Double tagged = projectile.getPersistentDataContainer().get(
                    plugin.combatDamageKey(), PersistentDataType.DOUBLE);
            if (tagged != null && tagged > 0.0) return OptionalDouble.of(tagged);
        }
        Entity source = damager;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        RaidEnemy enemy = enemies.get(source.getUniqueId());
        if (enemy == null || enemy.combatDamage() <= 0.0) return OptionalDouble.empty();
        return OptionalDouble.of(enemy.combatDamage());
    }

    public void tagProjectile(Projectile projectile) {
        if (!(projectile.getShooter() instanceof Entity shooter)) return;
        OptionalDouble damage = combatDamage(shooter);
        if (damage.isEmpty()) return;
        projectile.getPersistentDataContainer().set(
                plugin.sessionKey(), PersistentDataType.STRING, sessionId.toString());
        projectile.getPersistentDataContainer().set(
                plugin.arenaKey(), PersistentDataType.STRING, sessionKey);
        projectile.getPersistentDataContainer().set(
                plugin.combatDamageKey(), PersistentDataType.DOUBLE, damage.getAsDouble());
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        projectiles.add(projectile.getUniqueId());
    }

    public boolean contains(Location location) {
        return arena.contains(location, settings.boundaryRadius());
    }

    public boolean isJoinable() {
        if (participants.size() >= settings.maxPlayers()) return false;
        return state == RaidState.WAITING || state == RaidState.COUNTDOWN
                || (settings.allowLateJoin() && (state == RaidState.INTERMISSION || state == RaidState.WAVE));
    }

    boolean addPlayer(Player player) {
        if (!isJoinable() || participants.contains(player.getUniqueId())) return false;
        UUID id = player.getUniqueId();
        UUID character = manager.activeCharacter(id);
        Location returnLocation = player.getLocation().clone();
        if (shouldRestorePlayer()) {
            returnLocations.put(id, returnLocation);
            manager.rememberReturn(character, returnLocation);
        }
        if (settings.teleportOnJoin() && !player.teleport(arena.lobby())) {
            returnLocations.remove(id);
            if (shouldRestorePlayer()) manager.clearReturn(character);
            return false;
        }
        participants.add(id);
        participantCharacters.put(id, character);
        participantNames.put(id, player.getName());
        contributions.put(id, new Contribution());
        sidebar.show(player, sidebarSnapshot());
        player.showBossBar(bossBar);
        Messages.send(plugin, player, "joined", "&a你加入了浮岛夜袭场地 &f{arena}&a。",
                Map.of("arena", arena.id(), "instance", arena.id()));
        broadcast("player-joined", "&e{player} &7加入夜袭（{current}/{max}）",
                Map.of("player", player.getName(), "current", participants.size(), "max", settings.maxPlayers()));
        ensureArenaLoaded();
        ensureDisplays();
        if (settings.autoStart() && state == RaidState.WAITING && participants.size() >= settings.minPlayers()) {
            startCountdown(false);
        }
        return true;
    }

    public void removePlayer(Player player, boolean restore, String reason) {
        UUID id = player.getUniqueId();
        if (!participants.remove(id)) return;
        UUID character = participantCharacters.remove(id);
        player.hideBossBar(bossBar);
        if (restore && shouldRestorePlayer()) {
            Location back = returnLocations.get(id);
            if (back != null && back.getWorld() != null && player.teleport(back)) manager.clearReturn(character);
            else manager.evacuateFromDedicatedWorld(player);
        }
        sidebar.hide(player);
        returnLocations.remove(id);
        repairCooldowns.remove(id);
        contributions.remove(id);
        participantNames.remove(id);
        disconnectedAt.remove(id);
        manager.unregisterParticipant(id, this);
        Messages.send(plugin, player, "left", "&e你已离开浮岛夜袭。" + (reason.isBlank() ? "" : " &7" + reason));
        broadcast("player-left", "&e{player} &7离开了夜袭。", Map.of("player", player.getName()));
        if (participants.isEmpty()) {
            finish(RaidState.STOPPED, "所有玩家均已离开");
        } else if (state == RaidState.COUNTDOWN && participants.size() < settings.minPlayers()) {
            state = RaidState.WAITING;
            phaseTicks = 0;
            broadcast("countdown-cancelled", "&c人数不足，开始倒计时已取消。", Map.of());
        }
    }

    public void playerDisconnected(Player player) {
        UUID id = player.getUniqueId();
        if (!participants.contains(id)) return;
        disconnectedAt.putIfAbsent(id, clockTicks);
        player.hideBossBar(bossBar);
        sidebar.forget(player);
        broadcast("player-disconnected", "&e{player} &7断开连接，保留席位 {seconds} 秒。",
                Map.of("player", player.getName(), "seconds", seconds(settings.disconnectGraceTicks())));
    }

    public void playerReconnected(Player player) {
        UUID id = player.getUniqueId();
        if (!participants.contains(id) || !manager.activeCharacter(id).equals(participantCharacters.get(id))) return;
        disconnectedAt.remove(id);
        participantNames.put(id, player.getName());
        player.showBossBar(bossBar);
        if (!state.terminal() && !contains(player.getLocation()) && !player.teleport(arena.lobby())) {
            removePlayer(player, true, "无法返回所属竞技场实例");
            return;
        }
        sidebar.show(player, sidebarSnapshot());
        Messages.send(plugin, player, "reconnected", "&a你已重新连接到浮岛夜袭。");
    }

    public boolean startCountdown(boolean administrator) {
        if (state != RaidState.WAITING || participants.size() < settings.minPlayers()) return false;
        state = RaidState.COUNTDOWN;
        phaseTicks = settings.countdownTicks();
        lastAnnouncedSecond = Integer.MIN_VALUE;
        broadcast("countdown-started", administrator
                        ? "&e管理员启动了夜袭，&f{seconds} 秒&e后开始。"
                        : "&e人数已满足，&f{seconds} 秒&e后开始夜袭。",
                Map.of("seconds", seconds(phaseTicks)));
        return true;
    }

    public void stop(String reason) {
        finish(RaidState.STOPPED, reason);
    }

    public Optional<Contribution> contribution(UUID playerId) {
        return Optional.ofNullable(contributions.get(playerId));
    }

    public boolean addContribution(UUID playerId, ContributionType type, double amount) {
        Contribution contribution = contributions.get(playerId);
        if (contribution == null || state.terminal()) return false;
        contribution.add(type, amount);
        return true;
    }

    public void recordEnemyDamage(Player player, LivingEntity enemy, double finalDamage) {
        if (!enemies.containsKey(enemy.getUniqueId())) return;
        addContribution(player.getUniqueId(), ContributionType.DAMAGE,
                Math.min(Math.max(0.0, finalDamage), Math.max(0.0, enemy.getHealth())));
    }

    public void recordDefense(Player player, double finalDamage) {
        addContribution(player.getUniqueId(), ContributionType.DEFENSE, Math.max(0.0, finalDamage));
    }

    public void enemyDied(LivingEntity entity, Player killer) {
        RaidEnemy removed = enemies.remove(entity.getUniqueId());
        if (removed == null) return;
        manager.unregisterEnemy(entity.getUniqueId(), this);
        if (killer != null && participants.contains(killer.getUniqueId())) {
            addContribution(killer.getUniqueId(), ContributionType.KILL, 1.0);
        }
    }

    public boolean repair(Player player, Location interaction, EquipmentSlot hand) {
        if (state != RaidState.WAVE && state != RaidState.INTERMISSION) return false;
        Lane lane = nearestWard(interaction);
        if (lane == null) return false;
        if (!participants.contains(player.getUniqueId())) {
            Messages.send(plugin, player, "not-participant", "&c你不是本场夜袭的参与者。");
            return true;
        }
        if (breached.contains(lane)) {
            Messages.send(plugin, player, "ward-breached", "&c{lane}已经失守，本局无法重建。",
                    Map.of("lane", lane.displayName()));
            return true;
        }
        double current = wardHealth(lane);
        if (current >= settings.wardMaxHealth() - 0.0001) {
            Messages.send(plugin, player, "ward-full", "&e{lane}结界无需修复。",
                    Map.of("lane", lane.displayName()));
            return true;
        }
        long readyAt = repairCooldowns.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2)
                + settings.repairCooldownTicks();
        if (clockTicks < readyAt) return true;
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (item.getType() != settings.repairMaterial() || item.getAmount() < 1) {
            Messages.send(plugin, player, "repair-material", "&c修复需要 1 个 {material}。",
                    Map.of("material", displayMaterial(settings.repairMaterial())));
            return true;
        }
        if (player.getGameMode() != GameMode.CREATIVE || !player.hasPermission("ykntd.repair.free")) {
            item.setAmount(item.getAmount() - 1);
        }
        double actual = Math.min(settings.repairAmount(), settings.wardMaxHealth() - current);
        wardHealth.put(lane, current + actual);
        repairCooldowns.put(player.getUniqueId(), clockTicks);
        addContribution(player.getUniqueId(), ContributionType.REPAIR, actual);
        Location effect = arena.ward(lane).add(0.5, 1.0, 0.5);
        effect.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effect, 18, 0.5, 0.5, 0.5, 0.05);
        effect.getWorld().playSound(effect, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.25f);
        Messages.send(plugin, player, "repaired", "&a已修复 {lane} &f+{amount} &7（{health}/{max}）",
                Map.of("lane", lane.displayName(), "amount", NUMBER.format(actual),
                        "health", NUMBER.format(wardHealth(lane)), "max", NUMBER.format(settings.wardMaxHealth())));
        updateDisplays();
        return true;
    }

    private void tick() {
        if (state.terminal()) return;
        clockTicks += settings.tickPeriodTicks();
        validateParticipants();
        if (state.terminal()) return;
        switch (state) {
            case WAITING -> {
                if (settings.autoStart() && participants.size() >= settings.minPlayers()) startCountdown(false);
            }
            case COUNTDOWN -> tickCountdown();
            case WAVE -> tickWave();
            case INTERMISSION -> tickIntermission();
            default -> {
            }
        }
        if (clockTicks % 20L < settings.tickPeriodTicks()) updateHud();
    }

    private void tickCountdown() {
        if (participants.size() < settings.minPlayers()) {
            state = RaidState.WAITING;
            broadcast("countdown-cancelled", "&c人数不足，开始倒计时已取消。", Map.of());
            return;
        }
        announceCountdown();
        phaseTicks -= settings.tickPeriodTicks();
        if (phaseTicks <= 0) beginNextWave();
    }

    private void tickIntermission() {
        announceCountdown();
        phaseTicks -= settings.tickPeriodTicks();
        if (phaseTicks <= 0) beginNextWave();
    }

    private void tickWave() {
        waveTicks += settings.tickPeriodTicks();
        WaveDefinition wave = waves.get(waveIndex);
        if (!spawnQueue.isEmpty() && enemies.size() < settings.mobCap() && clockTicks >= nextSpawnTick) {
            spawnNext(wave);
            if (state.terminal()) return;
            nextSpawnTick = clockTicks + wave.spawnIntervalTicks();
        }
        tickEnemies();
        if (state.terminal()) return;
        if (coreHealth <= 0.0) {
            finish(RaidState.FAILED, "浮岛核心已经毁坏");
            return;
        }
        if (spawnQueue.isEmpty() && enemies.isEmpty()) {
            if (waveIndex + 1 >= waves.size()) {
                finish(RaidState.VICTORY, "所有夜袭波次均已击退");
            } else {
                state = RaidState.INTERMISSION;
                phaseTicks = settings.intermissionTicks();
                lastAnnouncedSecond = Integer.MIN_VALUE;
                broadcast("wave-cleared", "&a第 {wave} 波已清除，下一波将在 {seconds} 秒后开始。",
                        Map.of("wave", wave.number(), "seconds", seconds(phaseTicks)));
            }
            return;
        }
        if (waveTicks >= wave.timeLimitTicks()) {
            finish(RaidState.FAILED, "第 " + wave.number() + " 波防守超时");
        }
    }

    private void beginNextWave() {
        waveIndex++;
        if (waveIndex >= waves.size()) {
            finish(RaidState.VICTORY, "所有夜袭波次均已击退");
            return;
        }
        state = RaidState.WAVE;
        scalingPlayers = Math.max(1, onlineParticipantCount());
        waveTicks = 0;
        spawnQueue.clear();
        consecutiveSpawnFailures = 0;
        WaveDefinition wave = waves.get(waveIndex);
        if (waveIndex == 0) partyPower = snapshotPartyPower();
        powerHealthMultiplier = partyPower.healthMultiplier(wave.recommendedAuraLevel());
        powerDamageMultiplier = partyPower.damageMultiplier(wave.recommendedAuraLevel());
        for (MobGroup group : wave.groups()) {
            int amount = settings.scaling().scaleCountByLane(
                    group.amount(), scalingPlayers, group.lanes().size());
            for (int index = 0; index < amount; index++) {
                Lane lane = group.lanes().get(index % group.lanes().size());
                spawnQueue.addLast(new SpawnRequest(group, lane));
            }
        }
        nextSpawnTick = clockTicks;
        broadcast("wave-started",
                "&c⚔ {name}开始！ &7人数：{players}。",
                Map.of("name", PlayerMessagePolicy.sanitizeWaveName(wave.name()),
                        "players", scalingPlayers));
        updateHud();
    }

    private void spawnNext(WaveDefinition wave) {
        SpawnRequest request = spawnQueue.pollFirst();
        if (request == null) return;
        Location spawn = arena.spawn(request.lane());
        LivingEntity entity = createEntity(request.group(), spawn);
        if (entity == null) {
            spawnQueue.addFirst(request);
            consecutiveSpawnFailures++;
            if (consecutiveSpawnFailures >= 3) {
                finish(RaidState.FAILED,
                        "怪物 " + request.group().id() + " 连续生成失败，请检查 waves.yml 和依赖插件");
            }
            return;
        }
        consecutiveSpawnFailures = 0;
        configureEntity(entity, request.group(), request.lane());
        double combatDamage = request.group().baseDamage() <= 0.0 ? -1.0
                : request.group().baseDamage()
                * request.group().damageMultiplier()
                * settings.scaling().damageMultiplier(scalingPlayers)
                * powerDamageMultiplier;
        RaidEnemy enemy = new RaidEnemy(entity.getUniqueId(), request.lane(),
                request.group().wardDamage() * settings.scaling().damageMultiplier(scalingPlayers),
                combatDamage, clockTicks);
        enemies.put(entity.getUniqueId(), enemy);
        manager.registerEnemy(entity.getUniqueId(), this);
    }

    private LivingEntity createEntity(MobGroup group, Location spawn) {
        if (group.provider() == MobProvider.MYTHIC) {
            return mythicMobs.spawn(group.id(), spawn, group.level());
        }
        EntityType type;
        try {
            type = EntityType.valueOf(group.id().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("waves.yml 使用了未知原版实体：" + group.id());
            return null;
        }
        Entity raw;
        try {
            raw = spawn.getWorld().spawnEntity(spawn, type);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("无法生成原版实体 " + group.id() + "：" + exception.getMessage());
            return null;
        }
        if (raw instanceof LivingEntity living) return living;
        raw.remove();
        plugin.getLogger().warning("波次实体不是生物，已忽略：" + group.id());
        return null;
    }

    private void configureEntity(LivingEntity entity, MobGroup group, Lane lane) {
        entity.getPersistentDataContainer().set(plugin.sessionKey(), PersistentDataType.STRING, sessionId.toString());
        entity.getPersistentDataContainer().set(plugin.arenaKey(), PersistentDataType.STRING, sessionKey);
        entity.getPersistentDataContainer().set(plugin.laneKey(), PersistentDataType.STRING,
                lane.key());
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setFireTicks(0);
        if (entity instanceof Mob mob) {
            mob.setCanPickupItems(false);
            mob.clearLootTable();
        }
        disableEquipmentDrops(entity.getEquipment());

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        double desiredHealth = entity.getHealth();
        if (health != null) {
            double baseHealth = group.baseHealth() > 0.0 ? group.baseHealth() : health.getBaseValue();
            desiredHealth = clamp(baseHealth
                    * group.healthMultiplier()
                    * settings.scaling().healthMultiplier(scalingPlayers)
                    * powerHealthMultiplier, 1.0, 100_000_000.0);
            health.setBaseValue(desiredHealth);
            entity.setHealth(desiredHealth);
        }
        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        double configuredAttack = group.baseDamage() > 0.0
                ? group.baseDamage() : attack == null ? -1.0 : attack.getBaseValue();
        double desiredAttack = attack == null || configuredAttack < 0.0 ? -1.0 : clamp(configuredAttack
                * group.damageMultiplier()
                * settings.scaling().damageMultiplier(scalingPlayers)
                * powerDamageMultiplier, 0.0, 1_000_000.0);
        if (attack != null) attack.setBaseValue(desiredAttack);
        if (group.boss()) {
            entity.customName(Text.component("&c☠ &f" + group.id()));
            entity.setCustomNameVisible(true);
        }
        double finalDesiredHealth = desiredHealth;
        double finalDesiredAttack = desiredAttack;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!entity.isValid() || entity.isDead()) return;
            AttributeInstance latestHealth = entity.getAttribute(Attribute.MAX_HEALTH);
            if (latestHealth != null) {
                double fraction = latestHealth.getValue() <= 0.0 ? 1.0 : entity.getHealth() / latestHealth.getValue();
                latestHealth.setBaseValue(finalDesiredHealth);
                entity.setHealth(clamp(finalDesiredHealth * fraction, 0.01, finalDesiredHealth));
            }
            AttributeInstance latestAttack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (latestAttack != null && finalDesiredAttack >= 0.0) latestAttack.setBaseValue(finalDesiredAttack);
        });
    }

    private void disableEquipmentDrops(EntityEquipment equipment) {
        if (equipment == null) return;
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    private void tickEnemies() {
        var iterator = enemies.entrySet().iterator();
        while (iterator.hasNext()) {
            RaidEnemy enemy = iterator.next().getValue();
            Entity raw = plugin.getServer().getEntity(enemy.entityId());
            if (!(raw instanceof LivingEntity entity) || !entity.isValid() || entity.isDead()) {
                iterator.remove();
                manager.unregisterEnemy(enemy.entityId(), this);
                continue;
            }
            Location target = routeTarget(enemy);
            if (target == null || target.getWorld() == null || !target.getWorld().equals(entity.getWorld())) {
                finish(RaidState.FAILED, "场地 " + arena.id() + " 的 "
                        + enemy.lane().displayName() + " 路径配置无效");
                return;
            }
            List<Location> waypoints = arena.waypoints(enemy.lane());
            boolean targetingWaypoint = enemy.waypointIndex() < waypoints.size();
            double distanceSquared = entity.getLocation().distanceSquared(target);
            if (targetingWaypoint && distanceSquared <= 2.25) {
                enemy.advanceWaypoint(clockTicks);
                target = routeTarget(enemy);
                if (target == null) continue;
                distanceSquared = entity.getLocation().distanceSquared(target);
            }
            if (!targetingWaypoint && distanceSquared <= objectiveRangeSquared(enemy.lane())) {
                if (enemy.canAttack(clockTicks, settings.enemyAttackIntervalTicks())) {
                    attackObjective(entity, enemy);
                    enemy.attacked(clockTicks);
                }
                continue;
            }
            if (enemy.shouldRetarget(clockTicks, settings.retargetIntervalTicks())) {
                enemy.retargeted(clockTicks);
                if (entity instanceof Mob mob) {
                    Player nearby = nearestParticipant(entity.getLocation(), settings.playerAggroRange());
                    if (nearby != null) {
                        mob.setTarget(nearby);
                        enemy.resetProgress(clockTicks);
                    } else {
                        mob.setTarget(null);
                        mob.getPathfinder().moveTo(target, settings.pathSpeed());
                    }
                }
            }
            if (enemy.updateProgress(entity.getLocation(), target, clockTicks, STUCK_TICKS)) {
                Location safe = target.clone().add(0.0, 0.1, 0.0);
                entity.teleport(safe);
                enemy.resetProgress(clockTicks);
            }
        }
    }

    private Location routeTarget(RaidEnemy enemy) {
        List<Location> waypoints = arena.waypoints(enemy.lane());
        if (enemy.waypointIndex() < waypoints.size()) return waypoints.get(enemy.waypointIndex());
        return breached.contains(enemy.lane()) ? arena.core() : arena.ward(enemy.lane());
    }

    private double objectiveRangeSquared(Lane lane) {
        double range = breached.contains(lane) ? settings.coreAttackRange() : settings.wardAttackRange();
        return range * range;
    }

    private void attackObjective(LivingEntity entity, RaidEnemy enemy) {
        if (entity instanceof Mob mob) mob.swingMainHand();
        if (breached.contains(enemy.lane())) {
            coreHealth = Math.max(0.0, coreHealth - enemy.objectiveDamage());
            Location core = arena.core();
            core.getWorld().playSound(core, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.65f);
            if (settings.removeOnCoreHit()) {
                entity.remove();
                manager.unregisterEnemy(enemy.entityId(), this);
            }
        } else {
            double next = Math.max(0.0, wardHealth(enemy.lane()) - enemy.objectiveDamage());
            wardHealth.put(enemy.lane(), next);
            Location ward = arena.ward(enemy.lane());
            ward.getWorld().playSound(ward, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
            if (next <= 0.0 && breached.add(enemy.lane())) {
                broadcast("ward-destroyed", "&4⚠ {lane}结界已经失守！该路怪物将攻击核心。",
                        Map.of("lane", enemy.lane().displayName()));
            }
        }
        updateDisplays();
        updateHud();
    }

    private void validateParticipants() {
        for (UUID id : List.copyOf(participants)) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                long disconnected = disconnectedAt.computeIfAbsent(id, ignored -> clockTicks);
                if (clockTicks - disconnected >= settings.disconnectGraceTicks()) {
                    participants.remove(id);
                    manager.unregisterParticipant(id, this);
                }
                continue;
            }
            disconnectedAt.remove(id);
            if (!state.terminal() && !contains(player.getLocation()) && !player.isDead()) {
                if (player.teleport(arena.lobby())) {
                    Messages.send(plugin, player, "returned-to-arena", "&e你离开了场地区域，已被送回大厅。");
                } else {
                    removePlayer(player, true, "无法返回所属竞技场实例");
                    if (settled) return;
                }
            }
        }
        int online = onlineParticipantCount();
        if (online == 0 && !participants.isEmpty()) {
            if (allOfflineSince < 0L) allOfflineSince = clockTicks;
            if (clockTicks - allOfflineSince >= settings.allOfflineFailTicks()) {
                finish(state == RaidState.WAVE || state == RaidState.INTERMISSION
                        ? RaidState.FAILED : RaidState.STOPPED, "所有玩家均已离线");
            }
        } else {
            allOfflineSince = -1L;
        }
        if (participants.isEmpty()) finish(RaidState.STOPPED, "所有玩家均已离线或超过重连宽限期");
    }

    private void announceCountdown() {
        int remaining = seconds(phaseTicks);
        if (remaining != lastAnnouncedSecond && (remaining <= 5 || remaining % 5 == 0)) {
            lastAnnouncedSecond = remaining;
            String key = state == RaidState.COUNTDOWN ? "countdown" : "intermission";
            String fallback = state == RaidState.COUNTDOWN
                    ? "&e夜袭将在 &f{seconds} &e秒后开始。"
                    : "&e下一波将在 &f{seconds} &e秒后开始。";
            broadcast(key, fallback, Map.of("seconds", remaining));
        }
    }

    private void updateHud() {
        float progress = (float) clamp(coreHealth / settings.coreMaxHealth(), 0.0, 1.0);
        bossBar.progress(progress);
        // BetterHud reserves yellow boss bars as its shader carrier, so a yellow
        // gameplay bar loses its vanilla progress texture. Purple is not reserved.
        bossBar.color(BossBar.Color.valueOf(CoreBossBarColorPolicy.tone(progress).name()));
        bossBar.name(Text.component("&c浮岛核心 &f" + NUMBER.format(coreHealth) + "/"
                + NUMBER.format(settings.coreMaxHealth()) + " &8| &e" + state.displayName()
                + " &8| &f" + waveNumber() + "/" + totalWaves()));
        RaidSidebarSnapshot snapshot = sidebarSnapshot();
        forEachOnline(player -> sidebar.show(player, snapshot));
        updateDisplays();
    }

    private RaidSidebarSnapshot sidebarSnapshot() {
        return new RaidSidebarSnapshot(
                state.displayName(), waveNumber(), totalWaves(), enemyCount(),
                onlineParticipantCount(), settings.maxPlayers(), coreHealth,
                settings.coreMaxHealth(), settings.wardMaxHealth(), wardHealth, breached);
    }

    private void ensureDisplays() {
        if (coreDisplay != null && coreDisplay.isValid()) return;
        coreDisplay = createDisplay(arena.core().clone().add(0.0, 2.3, 0.0));
        for (Lane lane : Lane.values()) {
            wardDisplays.put(lane, createDisplay(arena.ward(lane).clone().add(0.0, 2.0, 0.0)));
        }
        updateDisplays();
    }

    private TextDisplay createDisplay(Location location) {
        return location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.getPersistentDataContainer().set(plugin.sessionKey(), PersistentDataType.STRING, sessionId.toString());
            display.getPersistentDataContainer().set(plugin.arenaKey(), PersistentDataType.STRING, sessionKey);
        });
    }

    private void updateDisplays() {
        if (coreDisplay != null && coreDisplay.isValid()) {
            coreDisplay.text(Text.component("&c&l浮岛核心\n&f" + NUMBER.format(coreHealth) + " / "
                    + NUMBER.format(settings.coreMaxHealth())));
        }
        for (Lane lane : Lane.values()) {
            TextDisplay display = wardDisplays.get(lane);
            if (display == null || !display.isValid()) continue;
            display.text(Text.component("&e&l" + lane.displayName() + "结界\n"
                    + (breached.contains(lane) ? "&4已失守" : "&f" + NUMBER.format(wardHealth(lane))
                    + " / " + NUMBER.format(settings.wardMaxHealth()))));
        }
    }

    private void ensureArenaLoaded() {
        addChunkTicket(arena.lobby());
        addChunkTicket(arena.core());
        for (Lane lane : Lane.values()) {
            List<Location> route = new ArrayList<>();
            route.add(arena.spawn(lane));
            route.addAll(arena.waypoints(lane));
            route.add(arena.ward(lane));
            route.add(arena.core());
            for (Location location : route) addChunkTicket(location);
            for (int index = 1; index < route.size(); index++) {
                addSegmentChunkTickets(route.get(index - 1), route.get(index));
            }
        }
    }

    private void addSegmentChunkTickets(Location start, Location end) {
        if (start == null || end == null || start.getWorld() == null
                || !start.getWorld().equals(end.getWorld())) return;
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / 8.0));
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            addRouteChunkTickets(new Location(start.getWorld(),
                    start.getX() + dx * progress,
                    start.getY(),
                    start.getZ() + dz * progress));
        }
    }

    private void addRouteChunkTickets(Location location) {
        addChunkTicket(location);
        addChunkTicket(location.clone().add(5.0, 0.0, 0.0));
        addChunkTicket(location.clone().add(-5.0, 0.0, 0.0));
        addChunkTicket(location.clone().add(0.0, 0.0, 5.0));
        addChunkTicket(location.clone().add(0.0, 0.0, -5.0));
    }

    private void addChunkTicket(Location location) {
        if (location == null || location.getWorld() == null) return;
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        ChunkRef ref = new ChunkRef(location.getWorld(), x, z);
        if (chunkTickets.add(ref)) location.getWorld().addPluginChunkTicket(x, z, plugin);
    }

    private Lane nearestWard(Location interaction) {
        Lane best = null;
        double bestDistance = settings.repairRange() * settings.repairRange();
        for (Lane lane : Lane.values()) {
            Location ward = arena.ward(lane);
            if (ward == null || interaction == null || interaction.getWorld() == null
                    || !interaction.getWorld().equals(ward.getWorld())) continue;
            double distance = interaction.distanceSquared(ward);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = lane;
            }
        }
        return best;
    }

    private Player nearestParticipant(Location location, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (UUID id : participants) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || player.isDead() || !player.getWorld().equals(location.getWorld())) continue;
            double distance = player.getLocation().distanceSquared(location);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private int onlineParticipantCount() {
        int count = 0;
        for (UUID id : participants) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) count++;
        }
        return count;
    }

    private PartyPowerProfile snapshotPartyPower() {
        List<Player> online = participants.stream()
                .map(plugin.getServer()::getPlayer)
                .filter(java.util.Objects::nonNull)
                .filter(Player::isOnline)
                .toList();
        try {
            return partyPowerProvider.snapshot(online);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("读取 AuraSkills 队伍战力失败，本局回退到 1.0："
                    + exception.getMessage());
            return PartyPowerProfile.unavailable();
        }
    }

    private void finish(RaidState result, String reason) {
        if (settled) return;
        settled = true;
        state = result;
        if (task != null) task.cancel();
        boolean victory = result == RaidState.VICTORY;
        String summary = victory ? "&a&l浮岛夜袭胜利！" : result == RaidState.FAILED
                ? "&c&l浮岛夜袭失败！" : "&e浮岛夜袭已终止。";
        try {
            broadcast("finished", "{result} &7{reason}", Map.of("result", summary, "reason", reason));
            for (UUID id : List.copyOf(participants)) {
                try {
                    settlePlayer(id, result, victory);
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("结算玩家 " + participantNames.getOrDefault(id, id.toString())
                            + " 时发生异常：" + exception.getMessage());
                }
            }
        } finally {
            cleanupSession();
        }
    }

    private void settlePlayer(UUID id, RaidState result, boolean victory) {
        UUID character = participantCharacters.get(id);
        Player player = character.equals(manager.activeCharacter(id)) ? plugin.getServer().getPlayer(id) : null;
        Contribution contribution = contributions.getOrDefault(id, new Contribution());
        double score = contribution.score(settings.contributionWeights());
        try {
            if (result != RaidState.STOPPED) {
                stats.record(character, participantNames.getOrDefault(id, id.toString()), victory,
                        waveNumber(), contribution, score);
            }
            if (player == null || !player.isOnline()) {
                if (result != RaidState.STOPPED) {
                    manager.queuePendingReward(id, character, participantNames.getOrDefault(id, id.toString()), arena.id(),
                            victory, waveNumber(), contribution, participants.size());
                }
                return;
            }
            if (result != RaidState.STOPPED) {
                RewardService.RewardResult reward = rewards.deliver(player, arena.id(), victory,
                        waveNumber(), contribution, settings.contributionWeights(), settings.minimumRewardScore(),
                        participants.size());
                Messages.send(plugin, player, "score-summary",
                        "&b本场贡献：&f{score} &7| 奖励档：{tier}",
                        Map.of("score", NUMBER.format(reward.score()), "tier", reward.tierName()));
            }
        } finally {
            if (player != null && player.isOnline()) {
                player.hideBossBar(bossBar);
                if (shouldRestorePlayer()) {
                    Location back = returnLocations.get(id);
                    if (back != null && back.getWorld() != null && player.teleport(back)) manager.clearReturn(character);
                    else manager.evacuateFromDedicatedWorld(player);
                }
                sidebar.hide(player);
            }
        }
    }

    private void cleanupSession() {
        for (RaidEnemy enemy : List.copyOf(enemies.values())) {
            try {
                Entity entity = plugin.getServer().getEntity(enemy.entityId());
                if (entity != null) entity.remove();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("清理夜袭怪物失败 " + enemy.entityId() + "：" + exception.getMessage());
            } finally {
                manager.unregisterEnemy(enemy.entityId(), this);
            }
        }
        enemies.clear();
        for (UUID projectileId : List.copyOf(projectiles)) {
            Entity projectile = plugin.getServer().getEntity(projectileId);
            if (projectile != null) projectile.remove();
        }
        projectiles.clear();
        spawnQueue.clear();
        try {
            if (coreDisplay != null) coreDisplay.remove();
            wardDisplays.values().forEach(Entity::remove);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("清理夜袭显示实体失败：" + exception.getMessage());
        }
        wardDisplays.clear();
        for (ChunkRef ticket : List.copyOf(chunkTickets)) {
            try {
                ticket.world().removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("移除夜袭区块票失败：" + exception.getMessage());
            }
        }
        chunkTickets.clear();
        sidebar.close();
        manager.sessionEnded(this);
    }

    private boolean shouldRestorePlayer() {
        return settings.restoreLocation()
                || plugin.arenaWorlds() != null
                && plugin.arenaWorlds().isDedicatedWorld(arena.core().getWorld());
    }

    private void broadcast(String key, String fallback, Map<String, ?> placeholders) {
        forEachOnline(player -> Messages.send(plugin, player, key, fallback, placeholders));
    }

    private void forEachOnline(java.util.function.Consumer<Player> action) {
        for (UUID id : participants) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                try {
                    action.accept(player);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("向夜袭玩家发送状态失败：" + exception.getMessage());
                }
            }
        }
    }

    private static int seconds(int ticks) {
        return Math.max(0, (int) Math.ceil(ticks / 20.0));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String displayMaterial(Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private record ChunkRef(World world, int x, int z) {
    }
}

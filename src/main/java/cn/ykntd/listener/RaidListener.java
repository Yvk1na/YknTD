package cn.ykntd.listener;

import cn.ykntd.YknTdPlugin;
import cn.ykntd.game.GameManager;
import cn.ykntd.game.RaidSession;
import cn.ykntd.util.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public final class RaidListener implements Listener {
    private final YknTdPlugin plugin;
    private final GameManager games;

    public RaidListener(YknTdPlugin plugin, GameManager games) {
        this.plugin = plugin;
        this.games = games;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConfiguredEnemyDamage(EntityDamageByEntityEvent event) {
        if (applyCombatRules(event)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        RaidSession session = sourceRaid(event.getDamager());
        if (session == null || !session.isParticipant(victim.getUniqueId())) return;
        session.combatDamage(event.getDamager()).ifPresent(event::setDamage);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombatRules(EntityDamageByEntityEvent event) {
        applyCombatRules(event);
    }

    private boolean applyCombatRules(EntityDamageByEntityEvent event) {
        RaidSession victimRaid = games.sessionByEnemy(event.getEntity()).orElse(null);
        Player attackingPlayer = sourcePlayer(event.getDamager());
        RaidSession attackingRaid = sourceRaid(event.getDamager());

        if (victimRaid != null) {
            if (attackingPlayer == null || !victimRaid.isParticipant(attackingPlayer.getUniqueId())) {
                event.setCancelled(true);
                return true;
            }
        }
        if (attackingRaid != null && event.getEntity() instanceof Player victim
                && !attackingRaid.isParticipant(victim.getUniqueId())) {
            event.setCancelled(true);
            return true;
        }
        if (!games.settings().friendlyFire()
                && attackingPlayer != null
                && event.getEntity() instanceof Player victim) {
            RaidSession attackerSession = games.session(attackingPlayer).orElse(null);
            RaidSession victimSession = games.session(victim).orElse(null);
            if (attackerSession != null && attackerSession == victimSession) {
                event.setCancelled(true);
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatContribution(EntityDamageByEntityEvent event) {
        RaidSession victimRaid = games.sessionByEnemy(event.getEntity()).orElse(null);
        Player attackingPlayer = sourcePlayer(event.getDamager());
        if (victimRaid != null && attackingPlayer != null && event.getEntity() instanceof LivingEntity enemy) {
            victimRaid.recordEnemyDamage(attackingPlayer, enemy, event.getFinalDamage());
        }
        RaidSession attackingRaid = sourceRaid(event.getDamager());
        if (attackingRaid != null && event.getEntity() instanceof Player victim
                && attackingRaid.isParticipant(victim.getUniqueId())) {
            attackingRaid.recordDefense(victim, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnemyDeath(EntityDeathEvent event) {
        RaidSession session = games.sessionByEnemy(event.getEntity()).orElse(null);
        suppressDrops(event);
        if (session != null) session.enemyDied(event.getEntity(), event.getEntity().getKiller());
    }

    // Other combat plugins can append drops after HIGHEST. YknTD loads after its
    // supported integrations, so this final pass makes the no-drop rule decisive.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFinalArenaDrops(EntityDeathEvent event) {
        suppressDrops(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaLootGenerate(LootGenerateEvent event) {
        Entity source = event.getLootContext().getLootedEntity();
        if (source == null) source = event.getEntity();
        if (!shouldSuppressDrops(source)) return;
        event.getLoot().clear();
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaEntityDrop(EntityDropItemEvent event) {
        if (shouldSuppressDrops(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        RaidSession session = games.sessionByEnemy(event.getEntity()).orElse(null);
        if (session == null || !(event.getTarget() instanceof Player player)) return;
        if (!session.isParticipant(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (games.sessionByEnemy(event.getEntity()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDaylightCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) return;
        if (games.sessionByEnemy(event.getEntity()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnemyProjectile(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Entity shooter)) return;
        games.sessionByEnemy(shooter).ifPresent(session -> session.tagProjectile(projectile));
    }

    private void suppressDrops(EntityDeathEvent event) {
        if (!shouldSuppressDrops(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private boolean shouldSuppressDrops(Entity entity) {
        if (entity == null) return false;
        boolean raidTagged = entity.getPersistentDataContainer().has(
                plugin.sessionKey(), PersistentDataType.STRING);
        return RaidDropPolicy.suppress(raidTagged, entity instanceof Player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRepair(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null || event.getHand() == null) return;
        Location interaction = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        RaidSession session = games.activeAt(interaction).orElse(null);
        if (session == null) return;
        if (session.repair(event.getPlayer(), interaction, event.getHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (games.isProtectedArena(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (games.isProtectedArena(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (games.isProtectedArena(event.getLocation())) event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (games.isProtectedArena(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (games.isProtectedArena(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (games.isProtectedArena(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> games.isProtectedArena(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> games.isProtectedArena(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (games.isProtectedArena(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleportIntoArena(PlayerTeleportEvent event) {
        if (event.getTo() == null || plugin.arenaWorlds() == null
                || !plugin.arenaWorlds().isDedicatedWorld(event.getTo().getWorld())) return;
        Player player = event.getPlayer();
        RaidSession ownSession = games.session(player).orElse(null);
        RaidSession targetSession = games.sessionAt(event.getTo().getWorld()).orElse(null);
        if ((ownSession == null || ownSession != targetSession) && !player.hasPermission("ykntd.admin")) {
            event.setCancelled(true);
            Messages.send(plugin, player, "dedicated-entry-denied",
                    "&c只能通过 &f/ykntd join &c进入自己所属的夜袭实例。");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        games.session(event.getPlayer()).ifPresent(session ->
                session.playerDisconnected(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RaidSession session = games.session(event.getPlayer()).orElse(null);
        if (session != null) session.playerReconnected(event.getPlayer());
        else {
            boolean restored = games.restorePending(event.getPlayer());
            if (!restored && plugin.arenaWorlds() != null
                    && plugin.arenaWorlds().isDedicatedWorld(event.getPlayer().getWorld())) {
                Location fallback = plugin.arenaWorlds().fallbackLocation();
                if (fallback != null) event.getPlayer().teleport(fallback);
            }
        }
        games.deliverPendingReward(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        games.session(event.getPlayer()).ifPresent(session ->
                event.setRespawnLocation(session.arena().lobby()));
    }

    @SuppressWarnings("removal")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNaturalArenaSpawn(CreatureSpawnEvent event) {
        if (plugin.arenaWorlds() == null
                || !plugin.arenaWorlds().isDedicatedWorld(event.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL
                || reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                || reason == CreatureSpawnEvent.SpawnReason.JOCKEY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        boolean dedicatedWorld = plugin.arenaWorlds() != null
                && plugin.arenaWorlds().isDedicatedWorld(event.getWorld());
        for (Entity entity : event.getChunk().getEntities()) {
            String tag = entity.getPersistentDataContainer().get(plugin.sessionKey(), PersistentDataType.STRING);
            if (tag != null && !games.ownsSessionTag(tag)) {
                entity.remove();
            } else if (tag == null && dedicatedWorld
                    && (entity instanceof Animals || entity instanceof Ambient || entity instanceof WaterMob)) {
                entity.remove();
            }
        }
    }

    private Player sourcePlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }

    private RaidSession sourceRaid(Entity damager) {
        RaidSession direct = games.sessionByEnemy(damager).orElse(null);
        if (direct != null) return direct;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return games.sessionByEnemy(shooter).orElse(null);
        }
        return null;
    }
}

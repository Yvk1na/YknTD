package cn.ykntd.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MythicMobsBridge {
    private final JavaPlugin plugin;
    private final Set<String> warnedIds = new HashSet<>();
    private Method mythicInstance;
    private Method mobManager;
    private Method spawnMob;
    private Method activeMobEntity;
    private Method bukkitEntity;

    public MythicMobsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    public boolean available() {
        Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        return mythic != null && mythic.isEnabled() && spawnMob != null;
    }

    public LivingEntity spawn(String id, Location location, double level) {
        if (!available()) {
            warnOnce(id, "MythicMobs 未启用，无法生成 Mythic 怪物：" + id);
            return null;
        }
        try {
            Object instance = mythicInstance.invoke(null);
            Object manager = mobManager.invoke(instance);
            Object activeMob = spawnMob.getParameterCount() == 3
                    ? spawnMob.invoke(manager, id, location, level)
                    : spawnMob.invoke(manager, id, location);
            if (activeMob == null) {
                warnOnce(id, "MythicMobs 未找到或未能生成怪物：" + id);
                return null;
            }
            Object abstractEntity = activeMobEntity.invoke(activeMob);
            Object rawEntity = bukkitEntity.invoke(abstractEntity);
            if (rawEntity instanceof LivingEntity living) return living;
            if (rawEntity instanceof Entity entity) entity.remove();
            warnOnce(id, "MythicMobs 怪物不是 LivingEntity：" + id);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(id, "生成 MythicMobs 怪物失败 " + id + "：" + exception.getMessage());
        }
        return null;
    }

    private void initialize() {
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            mythicInstance = mythicBukkit.getMethod("inst");
            mobManager = mythicBukkit.getMethod("getMobManager");
            Object instance = mythicInstance.invoke(null);
            Object manager = mobManager.invoke(instance);
            Class<?> managerClass = manager.getClass();
            try {
                spawnMob = managerClass.getMethod("spawnMob", String.class, Location.class, double.class);
            } catch (NoSuchMethodException ignored) {
                spawnMob = managerClass.getMethod("spawnMob", String.class, Location.class);
            }
            Class<?> activeMob = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            activeMobEntity = activeMob.getMethod("getEntity");
            bukkitEntity = activeMobEntity.getReturnType().getMethod("getBukkitEntity");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            mythicInstance = null;
            mobManager = null;
            spawnMob = null;
            activeMobEntity = null;
            bukkitEntity = null;
            Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (mythic != null) {
                plugin.getLogger().warning("MythicMobs API 不兼容，将只支持原版怪物：" + exception.getMessage());
            }
        }
    }

    private void warnOnce(String id, String message) {
        if (warnedIds.add(id)) plugin.getLogger().warning(message);
    }
}

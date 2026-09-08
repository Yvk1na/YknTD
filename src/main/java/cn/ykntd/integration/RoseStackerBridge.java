package cn.ykntd.integration;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class RoseStackerBridge {
    private RoseStackerBridge() {
    }

    @SuppressWarnings("unchecked")
    public static boolean register(JavaPlugin plugin) {
        Plugin roseStacker = plugin.getServer().getPluginManager().getPlugin("RoseStacker");
        if (roseStacker == null || !roseStacker.isEnabled()) return false;
        try {
            Class<?> rawEvent = Class.forName("dev.rosewood.rosestacker.event.EntityStackEvent");
            if (!Event.class.isAssignableFrom(rawEvent)) return false;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEvent;
            Method getStack = rawEvent.getMethod("getStack");
            Method getTargets = rawEvent.getMethod("getTargets");
            Class<?> stackedEntity = Class.forName("dev.rosewood.rosestacker.stack.StackedEntity");
            Method getEntity = stackedEntity.getMethod("getEntity");
            Listener listener = new Listener() { };
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.HIGHEST,
                    (ignored, event) -> {
                        if (!(event instanceof Cancellable cancellable)) return;
                        try {
                            if (isRaidStack(getStack.invoke(event), getEntity, plugin)) {
                                cancellable.setCancelled(true);
                                return;
                            }
                            Object targets = getTargets.invoke(event);
                            if (targets instanceof Iterable<?> iterable) {
                                for (Object target : iterable) {
                                    if (isRaidStack(target, getEntity, plugin)) {
                                        cancellable.setCancelled(true);
                                        return;
                                    }
                                }
                            }
                        } catch (ReflectiveOperationException exception) {
                            plugin.getLogger().warning("检查 RoseStacker 合并事件失败：" + exception.getMessage());
                        }
                    },
                    plugin,
                    true);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("RoseStacker 防堆叠钩子不可用：" + exception.getMessage());
            return false;
        }
    }

    private static boolean isRaidStack(Object stack, Method getEntity, JavaPlugin plugin)
            throws ReflectiveOperationException {
        if (stack == null) return false;
        Object entity = getEntity.invoke(stack);
        return entity instanceof Entity bukkitEntity
                && bukkitEntity.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "raid_session"), PersistentDataType.STRING);
    }
}

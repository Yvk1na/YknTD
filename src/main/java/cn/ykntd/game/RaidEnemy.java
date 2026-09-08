package cn.ykntd.game;

import cn.ykntd.model.Lane;
import org.bukkit.Location;

import java.util.UUID;

final class RaidEnemy {
    private final UUID entityId;
    private final Lane lane;
    private final double objectiveDamage;
    private final double combatDamage;
    private int waypointIndex;
    private long lastAttackTick;
    private long lastRetargetTick;
    private long lastProgressTick;
    private double bestDistanceSquared = Double.MAX_VALUE;

    RaidEnemy(UUID entityId, Lane lane, double objectiveDamage, double combatDamage, long currentTick) {
        this.entityId = entityId;
        this.lane = lane;
        this.objectiveDamage = objectiveDamage;
        this.combatDamage = combatDamage;
        lastAttackTick = currentTick;
        lastRetargetTick = Long.MIN_VALUE / 2;
        lastProgressTick = currentTick;
    }

    UUID entityId() {
        return entityId;
    }

    Lane lane() {
        return lane;
    }

    double objectiveDamage() {
        return objectiveDamage;
    }

    double combatDamage() {
        return combatDamage;
    }

    int waypointIndex() {
        return waypointIndex;
    }

    void advanceWaypoint(long tick) {
        waypointIndex++;
        resetProgress(tick);
    }

    boolean canAttack(long tick, int interval) {
        return tick - lastAttackTick >= interval;
    }

    void attacked(long tick) {
        lastAttackTick = tick;
    }

    boolean shouldRetarget(long tick, int interval) {
        return tick - lastRetargetTick >= interval;
    }

    void retargeted(long tick) {
        lastRetargetTick = tick;
    }

    boolean updateProgress(Location current, Location target, long tick, long stuckTicks) {
        double distance = current.distanceSquared(target);
        if (distance + 0.25 < bestDistanceSquared) {
            bestDistanceSquared = distance;
            lastProgressTick = tick;
            return false;
        }
        return tick - lastProgressTick >= stuckTicks;
    }

    void resetProgress(long tick) {
        bestDistanceSquared = Double.MAX_VALUE;
        lastProgressTick = tick;
    }
}

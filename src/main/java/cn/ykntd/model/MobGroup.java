package cn.ykntd.model;

import java.util.List;

public record MobGroup(
        MobProvider provider,
        String id,
        int amount,
        List<Lane> lanes,
        double level,
        double baseHealth,
        double baseDamage,
        double healthMultiplier,
        double damageMultiplier,
        double wardDamage,
        boolean boss
) {
    public MobGroup {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("怪物 ID 不能为空");
        if (amount < 1) throw new IllegalArgumentException("怪物数量必须大于 0");
        lanes = List.copyOf(lanes);
        if (lanes.isEmpty()) throw new IllegalArgumentException("怪物至少需要一条进攻路线");
        if (!Double.isFinite(level) || level <= 0.0) level = 1.0;
        if (!Double.isFinite(baseHealth) || baseHealth < 0.0) baseHealth = 0.0;
        if (!Double.isFinite(baseDamage) || baseDamage < 0.0) baseDamage = 0.0;
        if (!Double.isFinite(healthMultiplier) || healthMultiplier <= 0.0) healthMultiplier = 1.0;
        if (!Double.isFinite(damageMultiplier) || damageMultiplier <= 0.0) damageMultiplier = 1.0;
        if (!Double.isFinite(wardDamage) || wardDamage <= 0.0) wardDamage = 1.0;
    }
}

package cn.ykntd.model;

import java.util.List;

public record WaveDefinition(
        int number,
        String name,
        PowerStage stage,
        int recommendedAuraLevel,
        int spawnIntervalTicks,
        int timeLimitTicks,
        List<MobGroup> groups
) {
    public WaveDefinition {
        if (number < 1) throw new IllegalArgumentException("波次编号必须大于 0");
        name = name == null || name.isBlank() ? "第 " + number + " 波" : name;
        stage = stage == null ? PowerStage.forWave(number) : stage;
        recommendedAuraLevel = Math.max(0, Math.min(100, recommendedAuraLevel));
        spawnIntervalTicks = Math.max(1, spawnIntervalTicks);
        timeLimitTicks = Math.max(20, timeLimitTicks);
        groups = List.copyOf(groups);
        if (groups.isEmpty()) throw new IllegalArgumentException("波次 " + number + " 没有怪物组");
    }
}

package cn.ykntd.stats;

import java.util.UUID;

public record PlayerStats(
        UUID playerId,
        String name,
        int games,
        int wins,
        int bestWave,
        double totalScore
) {
}

package cn.ykntd.api;

import cn.ykntd.model.ContributionType;

import java.util.Map;

public record ContributionSnapshot(Map<ContributionType, Double> values, double score) {
    public ContributionSnapshot {
        values = Map.copyOf(values);
        score = Math.max(0.0, score);
    }
}

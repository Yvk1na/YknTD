package cn.ykntd.model;

import java.util.EnumMap;
import java.util.Map;

public final class Contribution {
    private final EnumMap<ContributionType, Double> values = new EnumMap<>(ContributionType.class);

    public Contribution() {
        for (ContributionType type : ContributionType.values()) values.put(type, 0.0);
    }

    public void add(ContributionType type, double amount) {
        if (type == null || !Double.isFinite(amount) || amount <= 0.0) return;
        values.merge(type, amount, Double::sum);
    }

    public double value(ContributionType type) {
        return values.getOrDefault(type, 0.0);
    }

    public double score(Map<ContributionType, Double> weights) {
        double result = 0.0;
        for (ContributionType type : ContributionType.values()) {
            double weight = weights.getOrDefault(type, 0.0);
            if (Double.isFinite(weight) && weight > 0.0) result += value(type) * weight;
        }
        return Math.max(0.0, result);
    }

    public Map<ContributionType, Double> snapshot() {
        return Map.copyOf(values);
    }
}

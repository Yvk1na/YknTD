package cn.ykntd.integration;

import cn.ykntd.model.PartyPowerProfile;
import cn.ykntd.model.AuraPowerIndex;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.stat.Stats;
import dev.aurelium.auraskills.api.trait.Traits;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AuraSkillsPowerBridge implements PartyPowerProvider {
    private final AuraSkillsApi api;

    public AuraSkillsPowerBridge() {
        api = AuraSkillsApi.get();
    }

    @Override
    public PartyPowerProfile snapshot(Collection<Player> players) {
        List<Double> offenseIndexes = new ArrayList<>();
        List<Double> defenseIndexes = new ArrayList<>();
        for (Player player : players) {
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user == null || !user.isLoaded()) continue;
            // The wave baselines already include the expected YknRpg equipment
            // and EcoPets budget for each stage. Base stat levels contain only
            // enabled-skill rewards, so gear, pets and talismans cannot be
            // counted for a second time by the runtime Aura scaling.
            double strength = user.getBaseStatLevel(Stats.STRENGTH);
            double healthStat = user.getBaseStatLevel(Stats.HEALTH);
            double toughness = user.getBaseStatLevel(Stats.TOUGHNESS);
            double regenerationStat = user.getBaseStatLevel(Stats.REGENERATION);
            double criticalChanceStat = user.getBaseStatLevel(Stats.CRIT_CHANCE);
            double criticalDamageStat = user.getBaseStatLevel(Stats.CRIT_DAMAGE);

            double attack = strength * Stats.STRENGTH.getTraitModifier(Traits.ATTACK_DAMAGE);
            double criticalChance = criticalChanceStat
                    * Stats.CRIT_CHANCE.getTraitModifier(Traits.CRIT_CHANCE);
            double criticalDamage = criticalDamageStat
                    * Stats.CRIT_DAMAGE.getTraitModifier(Traits.CRIT_DAMAGE);
            double health = healthStat * Stats.HEALTH.getTraitModifier(Traits.HP);
            double reduction = toughness
                    * Stats.TOUGHNESS.getTraitModifier(Traits.DAMAGE_REDUCTION);
            double regeneration = regenerationStat
                    * Stats.REGENERATION.getTraitModifier(Traits.SATURATION_REGEN);

            double offense = AuraPowerIndex.offenseIndex(attack, criticalChance, criticalDamage);
            double defense = AuraPowerIndex.defenseIndex(health, reduction, regeneration);
            if (Double.isFinite(offense) && Double.isFinite(defense)) {
                offenseIndexes.add(offense);
                defenseIndexes.add(defense);
            }
        }
        if (offenseIndexes.isEmpty()) return PartyPowerProfile.unavailable();
        return new PartyPowerProfile(true, median(offenseIndexes), median(defenseIndexes),
                offenseIndexes.size());
    }

    private double median(List<Double> values) {
        values.sort(Double::compareTo);
        int middle = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(middle - 1) + values.get(middle)) / 2.0
                : values.get(middle);
    }
}

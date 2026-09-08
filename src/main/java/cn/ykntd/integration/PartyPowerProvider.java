package cn.ykntd.integration;

import cn.ykntd.model.PartyPowerProfile;
import org.bukkit.entity.Player;

import java.util.Collection;

@FunctionalInterface
public interface PartyPowerProvider {
    PartyPowerProfile snapshot(Collection<Player> players);

    static PartyPowerProvider unavailable() {
        return ignored -> PartyPowerProfile.unavailable();
    }
}

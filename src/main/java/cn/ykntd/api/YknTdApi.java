package cn.ykntd.api;

import cn.ykntd.model.ContributionType;

import java.util.Optional;
import java.util.UUID;

/**
 * Small integration surface for combat/skill plugins that can authoritatively
 * attribute healing or crowd control to a raid participant. All methods must
 * be called from the server's primary thread.
 */
public interface YknTdApi {
    boolean isParticipant(UUID playerId);

    Optional<String> arenaOf(UUID playerId);

    boolean addContribution(UUID playerId, ContributionType type, double amount);

    Optional<ContributionSnapshot> contributionOf(UUID playerId);
}

package com.pvpwow.lfg.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, UUID> {

    /**
     * A plain equality, answered by a unique index
     * ({@code ux_players_battle_net_id}). A derived finder is the right tool
     * here: the trap is RANGES, where {@code AndExpiresAt} quietly means
     * equality and a scan.
     *
     * <p>There is no lookup by battle tag: the tag is mutable and not unique,
     * so it cannot identify a row.
     */
    Optional<PlayerEntity> findByBattleNetId(String battleNetId);
}

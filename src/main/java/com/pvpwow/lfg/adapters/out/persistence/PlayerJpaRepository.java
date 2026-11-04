package com.pvpwow.lfg.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface PlayerJpaRepository extends JpaRepository<PlayerEntity, UUID> {

    /**
     * The advisory pre-check, written to MATCH the functional unique index on
     * {@code lower(username)}. Spring Data's {@code IgnoreCase} keyword folds
     * with {@code upper()}, and a {@code lower()} index does not answer an
     * {@code upper()} predicate — the query would scan the table while the
     * index sat unused.
     */
    @Query("""
            select count(p) > 0
            from PlayerEntity p
            where lower(p.username) = lower(:username)
            """)
    boolean existsIgnoringUsernameCase(@Param("username") String username);

    /**
     * Battle tags are stored already case-folded by {@link
     * com.pvpwow.lfg.domain.player.BattleTag}, so equality is exact.
     */
    @Query("""
            select count(p) > 0
            from PlayerEntity p
            where p.battleTag = :battleTag
            """)
    boolean existsByBattleTag(@Param("battleTag") String battleTag);
}

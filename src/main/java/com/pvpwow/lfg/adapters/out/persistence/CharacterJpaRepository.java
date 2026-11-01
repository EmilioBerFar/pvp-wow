package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface CharacterJpaRepository extends JpaRepository<CharacterEntity, UUID> {

    /**
     * The advisory pre-check, written to MATCH the functional unique index on
     * {@code (region, realm, lower(name))}. Realm and region are already
     * normalized by their value objects, so only the name needs the
     * case-folding here.
     */
    @Query("""
            select count(c) > 0
            from CharacterEntity c
            where c.region = :region
              and c.realm = :realm
              and lower(c.name) = lower(:name)
            """)
    boolean existsIgnoringNameCase(@Param("region") Region region,
                                   @Param("realm") String realm,
                                   @Param("name") String name);
}

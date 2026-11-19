package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
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

    /**
     * The character with this natural key, whoever owns it.
     *
     * <p>The same query the pre-check uses, but returning the row instead of
     * a boolean: the import has to know WHOSE character it is before it
     * decides to refresh it or to leave it alone.
     */
    @Query("""
            select c
            from CharacterEntity c
            where c.region = :region
              and c.realm = :realm
              and lower(c.name) = lower(:name)
            """)
    Optional<CharacterEntity> findByNaturalKeyIgnoringNameCase(@Param("region") Region region,
                                                               @Param("realm") String realm,
                                                               @Param("name") String name);

    /**
     * The character Blizzard knows by this id, in this region.
     *
     * <p>HOW A CHARACTER IS RECOGNISED: by the id the provider gave it, not
     * by its name. A character that was renamed or whose realm moved is still
     * the same character, and the natural key cannot find it - the id can.
     * That is the whole reason the import does not duplicate characters.
     */
    @Query("""
            select c
            from CharacterEntity c
            where c.region = :region
              and c.blizzardCharacterId = :blizzardCharacterId
            """)
    Optional<CharacterEntity> findByBlizzardCharacterId(@Param("region") Region region,
                                                        @Param("blizzardCharacterId")
                                                        long blizzardCharacterId);
}

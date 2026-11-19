package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.domain.character.BlizzardCharacterId;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * The out port, implemented. It knows three things and refuses to know
 * others: how to translate the aggregate (the mapper), how to ask the
 * database a question, and that the database — not this class — owns the
 * duplicate rule.
 *
 * <p>WHY {@link EntityManager} AND NOT {@code repository.save}: the domain
 * assigns the id (a UUID created by the aggregate, not a sequence), so Spring
 * Data's "is it new?" heuristic answers NO for every character ever created
 * and routes the call to {@code merge}. Merge on a row that does not exist
 * yet did not insert here — it only issued the SELECT. Rather than teach the
 * entity to lie about its own newness, the adapter asks the database and
 * decides: absent means persist, present means merge. Identity decisions
 * belong to us, not to a heuristic.
 *
 * <p>WHY A PROGRAMMATIC TRANSACTION: {@code persist} requires one, and the
 * aggregate spans three tables (a half-saved character is a lying character).
 * {@code @Transactional} cannot be used here because it needs a proxy and
 * this class is {@code final} on purpose — the codebase's convention. The
 * template keeps the framework detail inside the adapter and makes the
 * boundary visible instead of magical.
 */
@Component
final class JpaCharacters implements Characters {

    private final CharacterJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final ReadOnlyTransactions reads;

    JpaCharacters(CharacterJpaRepository repository,
                  EntityManager entityManager,
                  PlatformTransactionManager transactionManager,
                  ReadOnlyTransactions reads) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
        this.reads = reads;
    }

    @Override
    public void save(Character character) {
        transactions.executeWithoutResult(status -> {
            CharacterEntity candidate = CharacterEntityMapper.toEntity(character);
            if (entityManager.find(CharacterEntity.class, candidate.id()) == null) {
                entityManager.persist(candidate);
            } else {
                entityManager.merge(candidate);
            }
        });
    }

    @Override
    public Optional<Character> findById(CharacterId id) {
        return reads.read(() ->
                repository.findById(id.value()).map(CharacterEntityMapper::toDomain));
    }

    @Override
    public Optional<Character> findByBlizzardId(Region region, BlizzardCharacterId blizzardId) {
        return reads.read(() ->
                repository.findByBlizzardCharacterId(region, blizzardId.value())
                        .map(CharacterEntityMapper::toDomain));
    }
}

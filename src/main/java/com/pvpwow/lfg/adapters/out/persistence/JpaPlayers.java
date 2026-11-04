package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * The Players port, implemented. Same shape and same reasons as {@link
 * JpaCharacters}: the domain owns the id, so Spring Data's newness heuristic
 * cannot be trusted; ask the database, then persist or merge.
 *
 * <p>The transaction is programmatic because the class is final by convention
 * and {@code @Transactional} needs a proxy. One insert today, but the boundary
 * is already the right one when the player grows a second table.
 */
@Component
final class JpaPlayers implements Players {

    private final PlayerJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    JpaPlayers(PlayerJpaRepository repository,
               EntityManager entityManager,
               PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void save(Player player) {
        transactions.executeWithoutResult(status -> {
            PlayerEntity candidate = PlayerEntityMapper.toEntity(player);
            if (entityManager.find(PlayerEntity.class, candidate.id()) == null) {
                entityManager.persist(candidate);
            } else {
                entityManager.merge(candidate);
            }
        });
    }

    @Override
    public Optional<Player> findById(PlayerId id) {
        return repository.findById(id.value()).map(PlayerEntityMapper::toDomain);
    }

    @Override
    public boolean usernameTaken(Username username) {
        return repository.existsIgnoringUsernameCase(username.value());
    }

    @Override
    public boolean battleTagTaken(BattleTag battleTag) {
        return repository.existsByBattleTag(battleTag.value());
    }
}

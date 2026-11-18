package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerTest {

    private static final BattleNetId ACCOUNT = new BattleNetId("123456789");

    @Test
    void registerCreatesAPlayerLinkedToTheAccountThatLoggedIn() {
        Player player = Player.register(ACCOUNT, new BattleTag("Emilio#12345"));

        assertThat(player.id()).isNotNull();
        assertThat(player.battleNetId()).isEqualTo(ACCOUNT);
        assertThat(player.battleTag().value()).isEqualTo("emilio#12345");
    }

    @Test
    void twoRegistersAreDifferentPlayersEvenWithIdenticalData() {
        assertThat(Player.register(ACCOUNT, new BattleTag("emilio#12345")))
                .isNotEqualTo(Player.register(ACCOUNT, new BattleTag("emilio#12345")));
    }

    @Test
    void sameIdMeansSamePlayerEvenAfterARename() {
        PlayerId id = new PlayerId(UUID.randomUUID());
        Player before = Player.rehydrate(id, ACCOUNT, new BattleTag("emilio#12345"));
        Player after = before.rename(new BattleTag("otheremilio#99999"));

        assertThat(before).isEqualTo(after);
        assertThat(before.hashCode()).isEqualTo(after.hashCode());
        assertThat(after.battleTag().value()).isEqualTo("otheremilio#99999");
    }

    @Test
    void aRenameKeepsTheAccountThatOwnsThePlayer() {
        Player player = Player.register(ACCOUNT, new BattleTag("emilio#12345"));

        Player renamed = player.rename(new BattleTag("otheremilio#99999"));

        assertThat(renamed.battleNetId()).isEqualTo(ACCOUNT);
    }

    @Test
    void renamingToTheSameTagReturnsTheSamePlayer() {
        Player player = Player.register(ACCOUNT, new BattleTag("emilio#12345"));

        assertThat(player.rename(new BattleTag("EMILIO#12345"))).isSameAs(player);
    }

    @Test
    void twoPlayersMayCarryTheSameBattleTagBecauseBlizzardReclaimsThem() {
        Player first = Player.register(new BattleNetId("1"), new BattleTag("emilio#12345"));
        Player second = Player.register(new BattleNetId("2"), new BattleTag("emilio#12345"));

        assertThat(first.battleTag()).isEqualTo(second.battleTag());
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> Player.rehydrate(null, ACCOUNT, new BattleTag("emilio#12345")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Player.register(null, new BattleTag("emilio#12345")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Player.register(ACCOUNT, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Player.rehydrate(new PlayerId(UUID.randomUUID()), null,
                new BattleTag("emilio#12345")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aPlayerCannotExistWithoutAnAccount() {
        assertThatThrownBy(() -> Player.rehydrate(new PlayerId(UUID.randomUUID()), null,
                new BattleTag("emilio#12345")))
                .isInstanceOf(NullPointerException.class);
    }
}

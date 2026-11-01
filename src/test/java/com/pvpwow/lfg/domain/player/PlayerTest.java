package com.pvpwow.lfg.domain.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerTest {

    @Test
    void registerCreatesAPlayerWithGeneratedIdentity() {
        Player player = Player.register(new Username("emilio"), new BattleTag("Emilio#12345"));

        assertThat(player.id()).isNotNull();
        assertThat(player.username().value()).isEqualTo("emilio");
        assertThat(player.battleTag().value()).isEqualTo("emilio#12345");
    }

    @Test
    void twoRegistersAreDifferentPlayersEvenWithIdenticalData() {
        assertThat(Player.register(new Username("emilio"), new BattleTag("emilio#12345")))
                .isNotEqualTo(Player.register(new Username("emilio"), new BattleTag("emilio#12345")));
    }

    @Test
    void sameIdMeansSamePlayerEvenAfterChangingBattleTagOrUsername() {
        PlayerId id = new PlayerId(UUID.randomUUID());
        Player before = Player.rehydrate(id, new Username("emilio"),
                new BattleTag("emilio#12345"));
        Player after = Player.rehydrate(id, new Username("emilio.ber"),
                new BattleTag("othername#99999"));

        assertThat(before).isEqualTo(after);
        assertThat(before.hashCode()).isEqualTo(after.hashCode());
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> Player.rehydrate(null, new Username("emilio"),
                new BattleTag("emilio#12345")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Player.register(null, new BattleTag("emilio#12345")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Player.register(new Username("emilio"), null))
                .isInstanceOf(NullPointerException.class);
    }
}

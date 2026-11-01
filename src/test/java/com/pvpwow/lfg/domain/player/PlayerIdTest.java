package com.pvpwow.lfg.domain.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerIdTest {

    @Test
    void keepsTheUuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(new PlayerId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void sameUuidMeansSameIdentity() {
        UUID uuid = UUID.randomUUID();
        assertThat(new PlayerId(uuid)).isEqualTo(new PlayerId(uuid));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new PlayerId(null))
                .isInstanceOf(NullPointerException.class);
    }
}

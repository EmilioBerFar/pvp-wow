package com.pvpwow.lfg.domain.character;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterIdTest {

    @Test
    void keepsTheUuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(new CharacterId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void sameUuidMeansSameIdentity() {
        UUID uuid = UUID.randomUUID();
        assertThat(new CharacterId(uuid)).isEqualTo(new CharacterId(uuid));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new CharacterId(null))
                .isInstanceOf(NullPointerException.class);
    }
}

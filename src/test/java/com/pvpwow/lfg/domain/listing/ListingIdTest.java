package com.pvpwow.lfg.domain.listing;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingIdTest {

    @Test
    void keepsTheUuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(new ListingId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void sameUuidMeansSameIdentity() {
        UUID uuid = UUID.randomUUID();
        assertThat(new ListingId(uuid)).isEqualTo(new ListingId(uuid));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new ListingId(null))
                .isInstanceOf(NullPointerException.class);
    }
}

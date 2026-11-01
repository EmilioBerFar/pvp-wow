package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.Role;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LfgListingTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(6);

    @Test
    void publishStartsActiveAndDiesAfterTheTtl() {
        LfgListing listing = publishListing();

        assertThat(listing.id()).isNotNull();
        assertThat(listing.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.isActive()).isTrue();
        assertThat(listing.createdAt()).isEqualTo(NOW);
        assertThat(listing.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(listing.spec()).isEqualTo(Specialization.MAGE_FROST);
        assertThat(listing.bracket()).isEqualTo(Bracket.THREE_VS_THREE);
    }

    @Test
    void publishPinsTheSpecSoTheCompRoleIsDerivedNotStored() {
        assertThat(publishListing().spec().role()).isEqualTo(Role.RANGED_DPS);
    }

    @Test
    void rejectsAListingWithoutLanguages() {
        assertThatThrownBy(() -> LfgListing.publish(characterId(), Bracket.TWO_VS_TWO,
                Specialization.MAGE_FROST, Set.of(), schedule(), NOW, TTL))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThatThrownBy(() -> LfgListing.publish(characterId(), Bracket.TWO_VS_TWO,
                Specialization.MAGE_FROST, Set.of(Language.ES), schedule(), NOW, Duration.ZERO))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> LfgListing.publish(characterId(), Bracket.TWO_VS_TWO,
                Specialization.MAGE_FROST, Set.of(Language.ES), schedule(), NOW, Duration.ofMinutes(-1)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void cancelMovesAnActiveListingToCancelled() {
        LfgListing listing = publishListing();

        listing.cancel();

        assertThat(listing.status()).isEqualTo(ListingStatus.CANCELLED);
        assertThat(listing.isActive()).isFalse();
    }

    @Test
    void cancellingTwiceIsARuleViolation() {
        LfgListing listing = publishListing();
        listing.cancel();

        assertThatThrownBy(listing::cancel)
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void expireIfDueDoesNothingBeforeTheDeadline() {
        LfgListing listing = publishListing();

        assertThat(listing.expireIfDue(NOW.plus(TTL).minusSeconds(1))).isFalse();
        assertThat(listing.status()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void expireIfDueAtTheDeadlineExpiresTheListing() {
        LfgListing listing = publishListing();

        assertThat(listing.expireIfDue(NOW.plus(TTL))).isTrue();
        assertThat(listing.status()).isEqualTo(ListingStatus.EXPIRED);
    }

    @Test
    void expireIfDueIsIdempotent() {
        LfgListing listing = publishListing();
        listing.expireIfDue(NOW.plus(TTL));

        assertThat(listing.expireIfDue(NOW.plus(TTL).plusSeconds(60))).isFalse();
        assertThat(listing.status()).isEqualTo(ListingStatus.EXPIRED);
    }

    @Test
    void anExpiredListingCannotBeCancelled() {
        LfgListing listing = publishListing();
        listing.expireIfDue(NOW.plus(TTL));

        assertThatThrownBy(listing::cancel)
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void expireIfDueOnACancelledListingIsANoOpThatReturnsFalse() {
        // The expiration job sweeps every listing, including ones the player
        // cancelled seconds before their deadline. Terminal state wins: the
        // sweep must neither resurrect the listing nor crash on it — it just
        // reports "nothing transitioned".
        LfgListing listing = publishListing();
        listing.cancel();

        assertThat(listing.expireIfDue(NOW.plus(TTL).plusSeconds(60))).isFalse();
        assertThat(listing.status()).isEqualTo(ListingStatus.CANCELLED);
        assertThat(listing.isActive()).isFalse();
    }

    @Test
    void sameIdMeansSameListingEvenAfterAStateChange() {
        LfgListing original = publishListing();
        LfgListing rehydrated = LfgListing.rehydrate(original.id(), original.characterId(),
                original.bracket(), original.spec(), original.languages(), original.schedule(),
                ListingStatus.CANCELLED, original.createdAt(), original.expiresAt());

        assertThat(original).isEqualTo(rehydrated);
        assertThat(original.hashCode()).isEqualTo(rehydrated.hashCode());
        assertThat(original.status()).isNotEqualTo(rehydrated.status());
    }

    @Test
    void rehydrateRejectsStateThatExpiresBeforeItWasCreated() {
        assertThatThrownBy(() -> LfgListing.rehydrate(new ListingId(UUID.randomUUID()),
                characterId(), Bracket.TWO_VS_TWO, Specialization.MAGE_FROST,
                Set.of(Language.ES), schedule(), ListingStatus.ACTIVE, NOW, NOW.minusSeconds(1)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    private LfgListing publishListing() {
        return LfgListing.publish(characterId(), Bracket.THREE_VS_THREE,
                Specialization.MAGE_FROST, Set.of(Language.ES, Language.EN), schedule(), NOW, TTL);
    }

    private CharacterId characterId() {
        return new CharacterId(UUID.randomUUID());
    }

    private AvailabilitySchedule schedule() {
        return new AvailabilitySchedule(
                Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
                ZoneId.of("Europe/Madrid"));
    }
}

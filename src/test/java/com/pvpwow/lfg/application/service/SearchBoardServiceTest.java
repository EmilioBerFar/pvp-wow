package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.SearchBoard.BoardEntry;
import com.pvpwow.lfg.application.port.in.SearchBoard.SearchBoardQuery;
import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.BoardListings.BoardCandidate;
import com.pvpwow.lfg.application.port.out.BoardListings.SearchCriteria;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchBoardServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final PvpSeason CURRENT_SEASON = new PvpSeason(42);
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
            ZoneId.of("Europe/Madrid"));

    private final FakeBoardListings boardListings = new FakeBoardListings();
    private final SearchBoardService service = new SearchBoardService(
            boardListings, () -> CURRENT_SEASON, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void delegatesEveryFilterAndTheFreshnessWindowToTheReadPort() {
        SearchBoardQuery query = new SearchBoardQuery(Region.EU, Bracket.THREE_VS_THREE,
                WowClass.PRIEST, new Rating(1800), new ItemLevel(670));

        service.search(query);

        SearchCriteria criteria = boardListings.lastCriteria;
        assertThat(criteria.region()).isEqualTo(Region.EU);
        assertThat(criteria.bracket()).isEqualTo(Bracket.THREE_VS_THREE);
        assertThat(criteria.wowClass()).isEqualTo(WowClass.PRIEST);
        assertThat(criteria.minimumRating()).isEqualTo(new Rating(1800));
        assertThat(criteria.minimumItemLevel()).isEqualTo(new ItemLevel(670));
        assertThat(criteria.currentSeason()).isEqualTo(CURRENT_SEASON);
        assertThat(criteria.now()).isEqualTo(NOW);
        assertThat(criteria.ratingFreshAfter()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void returnsTheCandidatesWithTheirDomainEvidenceIntact() {
        BoardCandidate candidate = candidate(standing(1900, CURRENT_SEASON, NOW));
        boardListings.results.add(candidate);

        List<BoardEntry> entries = service.search(query());

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().listing()).isEqualTo(candidate.listing());
        assertThat(entries.getFirst().character()).isEqualTo(candidate.character());
        assertThat(entries.getFirst().character().gear().observedAt()).isEqualTo(NOW);
        assertThat(entries.getFirst().ratingStale()).isFalse();
    }

    @Test
    void marksARatingOlderThanThirtyDaysAsStaleWithoutRemovingIt() {
        boardListings.results.add(candidate(
                standing(1900, CURRENT_SEASON, NOW.minus(Duration.ofDays(30)).minusNanos(1))));

        List<BoardEntry> entries = service.search(query());

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.ratingStale()).isTrue();
            assertThat(entry.character().ratingFor(Bracket.TWO_VS_TWO))
                    .contains(new Rating(1900));
        });
    }

    @Test
    void exactlyThirtyDaysOldIsStillFresh() {
        boardListings.results.add(candidate(
                standing(1900, CURRENT_SEASON, NOW.minus(Duration.ofDays(30)))));

        assertThat(service.search(query())).singleElement()
                .satisfies(entry -> assertThat(entry.ratingStale()).isFalse());
    }

    @Test
    void aPreviousSeasonRatingIsStaleEvenIfItWasJustObserved() {
        boardListings.results.add(candidate(standing(2400, new PvpSeason(41), NOW)));

        assertThat(service.search(query())).singleElement()
                .satisfies(entry -> assertThat(entry.ratingStale()).isTrue());
    }

    @Test
    void anUnrankedCandidateHasNoRatingToMarkAsStale() {
        boardListings.results.add(candidate(null));

        assertThat(service.search(query())).singleElement()
                .satisfies(entry -> assertThat(entry.ratingStale()).isFalse());
    }

    private SearchBoardQuery query() {
        return new SearchBoardQuery(Region.EU, Bracket.TWO_VS_TWO, null, null, null);
    }

    private BoardCandidate candidate(PvpStanding standing) {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        Map<Bracket, PvpStanding> standings = standing == null
                ? Map.of()
                : Map.of(Bracket.TWO_VS_TWO, standing);
        Character character = Character.rehydrate(characterId, new PlayerId(UUID.randomUUID()),
                new CharacterName("Frostbolt"), new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), standings);
        LfgListing listing = LfgListing.publish(characterId, Bracket.TWO_VS_TWO,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE, NOW, Duration.ofHours(24));
        return new BoardCandidate(listing, character);
    }

    private PvpStanding standing(int rating, PvpSeason season, Instant observedAt) {
        return new PvpStanding(new Rating(rating), season, observedAt);
    }

    private static final class FakeBoardListings implements BoardListings {

        private final List<BoardCandidate> results = new ArrayList<>();
        private SearchCriteria lastCriteria;

        @Override
        public List<BoardCandidate> search(SearchCriteria criteria) {
            lastCriteria = criteria;
            return List.copyOf(results);
        }
    }
}

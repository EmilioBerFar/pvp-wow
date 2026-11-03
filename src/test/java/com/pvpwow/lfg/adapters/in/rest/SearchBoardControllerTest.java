package com.pvpwow.lfg.adapters.in.rest;

import com.jayway.jsonpath.JsonPath;
import com.pvpwow.lfg.application.port.in.SearchBoard;
import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.BoardListings.BoardCandidate;
import com.pvpwow.lfg.application.port.out.BoardListings.BoardSlice;
import com.pvpwow.lfg.application.port.out.BoardListings.SearchCriteria;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.application.service.SearchBoardService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice of the board: the REAL search service on a hand-rolled read fake.
 * It proves the query parameters become criteria, the stale flags reach the
 * wire, and the cursor really is opaque and round-trips.
 */
@WebMvcTest(SearchBoardController.class)
@Import(SearchBoardControllerTest.FakeWiring.class)
class SearchBoardControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final PvpSeason SEASON = new PvpSeason(42);
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
            ZoneId.of("Europe/Madrid"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardListings board;

    @BeforeEach
    void resetTheFake() {
        ((FakeBoardListings) board).reset();
    }

    @Test
    void returnsTheBoardPageWithTheEvidenceOfTheQueriedBracket() throws Exception {
        fake().results.add(candidate(1900, NOW, NOW));

        mockMvc.perform(get("/api/board").param("region", "EU").param("bracket", "2v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].bracket").value("2v2"))
                .andExpect(jsonPath("$.items[0].spec").value("MAGE_FROST"))
                .andExpect(jsonPath("$.items[0].schedule.zone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.items[0].character.name").value("Frostbolt"))
                .andExpect(jsonPath("$.items[0].character.realm").value("sanguino"))
                .andExpect(jsonPath("$.items[0].character.rating").value(1900))
                .andExpect(jsonPath("$.items[0].character.ratingStale").value(false))
                .andExpect(jsonPath("$.items[0].character.gearStale").value(false))
                .andExpect(jsonPath("$.items[0].character.ownerId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void anUnrankedCharacterStillAppearsWithNoRating() throws Exception {
        fake().results.add(candidate(null, NOW, NOW));

        mockMvc.perform(get("/api/board").param("region", "EU").param("bracket", "2v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].character.rating").isEmpty())
                .andExpect(jsonPath("$.items[0].character.ratingStale").value(false));
    }

    @Test
    void oldEvidenceArrivesFlaggedStaleButVisible() throws Exception {
        fake().results.add(candidate(1900,
                NOW.minus(Duration.ofDays(30)).minusNanos(1),
                NOW.minus(Duration.ofDays(30)).minusNanos(1)));

        mockMvc.perform(get("/api/board").param("region", "EU").param("bracket", "2v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].character.rating").value(1900))
                .andExpect(jsonPath("$.items[0].character.ratingStale").value(true))
                .andExpect(jsonPath("$.items[0].character.itemLevel").value(675))
                .andExpect(jsonPath("$.items[0].character.gearStale").value(true));
    }

    @Test
    void filtersAndPageSizeBecomeCriteria() throws Exception {
        mockMvc.perform(get("/api/board")
                        .param("region", "EU")
                        .param("bracket", "3v3")
                        .param("wowClass", "PRIEST")
                        .param("minRating", "1800")
                        .param("minItemLevel", "670")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        SearchCriteria criteria = fake().lastCriteria;
        assertThat(criteria.region()).isEqualTo(Region.EU);
        assertThat(criteria.bracket()).isEqualTo(Bracket.THREE_VS_THREE);
        assertThat(criteria.wowClass()).isEqualTo(WowClass.PRIEST);
        assertThat(criteria.minimumRating()).isEqualTo(new Rating(1800));
        assertThat(criteria.minimumItemLevel()).isEqualTo(new ItemLevel(670));
        assertThat(criteria.limit()).isEqualTo(5);
        assertThat(criteria.cursor()).isNull();
        assertThat(criteria.now()).isEqualTo(NOW);
    }

    @Test
    void theDefaultPageSizeIsTheUseCaseDefault() throws Exception {
        mockMvc.perform(get("/api/board").param("region", "EU").param("bracket", "2v2"))
                .andExpect(status().isOk());

        assertThat(fake().lastCriteria.limit()).isEqualTo(SearchBoard.DEFAULT_PAGE_SIZE);
    }

    @Test
    void aNonFinalPageReturnsAnOpaqueCursorThatTheNextRequestEchoes() throws Exception {
        fake().results.add(candidate(1900, NOW, NOW));
        fake().hasMore = true;

        MvcResult first = mockMvc.perform(get("/api/board")
                        .param("region", "EU").param("bracket", "2v2"))
                .andExpect(status().isOk())
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(cursor).isNotBlank();

        mockMvc.perform(get("/api/board")
                        .param("region", "EU").param("bracket", "2v2").param("cursor", cursor))
                .andExpect(status().isOk());

        LfgListing last = fake().results.getLast().listing();
        assertThat(fake().lastCriteria.cursor())
                .isEqualTo(new BoardListings.BoardCursor(last.createdAt(), last.id()));
    }

    @Test
    void aMissingRegionIsA400() throws Exception {
        mockMvc.perform(get("/api/board").param("bracket", "2v2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownBracketIsA422() throws Exception {
        mockMvc.perform(get("/api/board").param("region", "EU").param("bracket", "5v5"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Unknown bracket '5v5' — valid: 2v2, 3v3"));
    }

    @Test
    void aPageSizeOutsideTheBoundsIsA422() throws Exception {
        mockMvc.perform(get("/api/board")
                        .param("region", "EU").param("bracket", "2v2").param("limit", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 50, got: 0"));

        mockMvc.perform(get("/api/board")
                        .param("region", "EU").param("bracket", "2v2").param("limit", "51"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 50, got: 51"));
    }

    @Test
    void aCursorThisApiDidNotIssueIsA400() throws Exception {
        mockMvc.perform(get("/api/board")
                        .param("region", "EU").param("bracket", "2v2").param("cursor", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed cursor"));
    }

    private FakeBoardListings fake() {
        return (FakeBoardListings) board;
    }

    private BoardCandidate candidate(Integer rating, Instant ratingObservedAt,
                                     Instant gearObservedAt) {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        Map<Bracket, PvpStanding> standings = rating == null ? Map.of()
                : Map.of(Bracket.TWO_VS_TWO,
                        new PvpStanding(new Rating(rating), SEASON, ratingObservedAt));
        Character character = Character.rehydrate(characterId, new PlayerId(UUID.randomUUID()),
                new CharacterName("Frostbolt"), new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), gearObservedAt),
                Set.of(Specialization.MAGE_FROST), standings);
        LfgListing listing = LfgListing.publish(characterId, Bracket.TWO_VS_TWO,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE,
                NOW.minus(Duration.ofHours(1)), Duration.ofHours(24));
        return new BoardCandidate(listing, character);
    }

    @TestConfiguration
    static class FakeWiring {

        @Bean
        BoardListings boardListings() {
            return new FakeBoardListings();
        }

        @Bean
        Seasons seasons() {
            return () -> SEASON;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        SearchBoard searchBoard(BoardListings boardListings, Seasons seasons, Clock clock) {
            return new SearchBoardService(boardListings, seasons, clock);
        }
    }

    private static final class FakeBoardListings implements BoardListings {

        private final List<BoardCandidate> results = new ArrayList<>();
        private boolean hasMore;
        private SearchCriteria lastCriteria;

        void reset() {
            results.clear();
            hasMore = false;
            lastCriteria = null;
        }

        @Override
        public BoardSlice search(SearchCriteria criteria) {
            lastCriteria = criteria;
            return new BoardSlice(results, hasMore);
        }
    }
}

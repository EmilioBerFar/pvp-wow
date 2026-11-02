package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.SearchBoard;
import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.BoardListings.SearchCriteria;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.domain.character.PvpSeason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * SearchBoard, orchestrated. The read adapter executes filters and recency
 * ordering; this service supplies the current time and season and computes
 * the freshness labels without mutating the observed domain facts.
 *
 * <p>Ratings and gear age under the same policy: one window, one threshold.
 * Old evidence still reaches the board, but it is marked stale and it cannot
 * satisfy its own minimum filter.
 */
public final class SearchBoardService implements SearchBoard {

    private static final Duration MAX_EVIDENCE_AGE = Duration.ofDays(30);

    private final BoardListings boardListings;
    private final Seasons seasons;
    private final Clock clock;

    public SearchBoardService(BoardListings boardListings, Seasons seasons, Clock clock) {
        this.boardListings = Objects.requireNonNull(boardListings, "boardListings must not be null");
        this.seasons = Objects.requireNonNull(seasons, "seasons must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<BoardEntry> search(SearchBoardQuery query) {
        Instant now = clock.instant();
        Instant evidenceFreshAfter = now.minus(MAX_EVIDENCE_AGE);
        PvpSeason currentSeason = seasons.current();

        SearchCriteria criteria = new SearchCriteria(query.region(), query.bracket(),
                query.wowClass(), query.minimumRating(), query.minimumItemLevel(),
                currentSeason, evidenceFreshAfter, now);

        return boardListings.search(criteria).stream()
                .map(candidate -> new BoardEntry(
                        candidate.listing(),
                        candidate.character(),
                        isRatingStale(candidate, query, currentSeason, evidenceFreshAfter),
                        isGearStale(candidate, evidenceFreshAfter)))
                .toList();
    }

    private boolean isRatingStale(BoardListings.BoardCandidate candidate,
                                  SearchBoardQuery query,
                                  PvpSeason currentSeason,
                                  Instant evidenceFreshAfter) {
        return candidate.character().standingFor(query.bracket())
                .map(standing -> !standing.season().equals(currentSeason)
                        || standing.observedAt().isBefore(evidenceFreshAfter))
                .orElse(false);
    }

    private boolean isGearStale(BoardListings.BoardCandidate candidate,
                                Instant evidenceFreshAfter) {
        return candidate.character().gear().observedAt().isBefore(evidenceFreshAfter);
    }
}

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
 * the freshness label without mutating the observed domain facts.
 */
public final class SearchBoardService implements SearchBoard {

    private static final Duration MAX_RATING_AGE = Duration.ofDays(30);

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
        Instant ratingFreshAfter = now.minus(MAX_RATING_AGE);
        PvpSeason currentSeason = seasons.current();

        SearchCriteria criteria = new SearchCriteria(query.region(), query.bracket(),
                query.wowClass(), query.minimumRating(), query.minimumItemLevel(),
                currentSeason, ratingFreshAfter, now);

        return boardListings.search(criteria).stream()
                .map(candidate -> new BoardEntry(candidate.listing(), candidate.character(),
                        isStale(candidate, query, currentSeason, ratingFreshAfter)))
                .toList();
    }

    private boolean isStale(BoardListings.BoardCandidate candidate, SearchBoardQuery query,
                            PvpSeason currentSeason, Instant ratingFreshAfter) {
        return candidate.character().standingFor(query.bracket())
                .map(standing -> !standing.season().equals(currentSeason)
                        || standing.observedAt().isBefore(ratingFreshAfter))
                .orElse(false);
    }
}

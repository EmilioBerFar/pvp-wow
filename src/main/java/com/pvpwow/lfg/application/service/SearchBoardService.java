package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.SearchBoard;
import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.BoardListings.BoardSlice;
import com.pvpwow.lfg.application.port.out.BoardListings.SearchCriteria;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.domain.character.PvpSeason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * SearchBoard, orchestrated. The read adapter executes filters, recency
 * ordering and keyset paging; this service supplies the current time and season
 * and computes the freshness labels without mutating the observed domain facts.
 *
 * <p>Ratings and gear age under the same policy: one window, one threshold.
 * Old evidence still reaches the board, but it is marked stale and it cannot
 * satisfy its own minimum filter.
 *
 * <p>The page cursor crossing the out port is rebuilt here rather than shared,
 * so each port keeps its own vocabulary and neither imports the other.
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
    public BoardPage search(SearchBoardQuery query) {
        Instant now = clock.instant();
        Instant evidenceFreshAfter = now.minus(MAX_EVIDENCE_AGE);
        PvpSeason currentSeason = seasons.current();

        SearchCriteria criteria = new SearchCriteria(query.region(), query.bracket(),
                query.wowClass(), query.minimumRating(), query.minimumItemLevel(),
                currentSeason, evidenceFreshAfter, now, cursorOf(query.cursor()), query.limit());

        BoardSlice slice = boardListings.search(criteria);

        List<BoardEntry> entries = slice.candidates().stream()
                .map(candidate -> new BoardEntry(
                        candidate.listing(),
                        candidate.character(),
                        isRatingStale(candidate, query, currentSeason, evidenceFreshAfter),
                        isGearStale(candidate, evidenceFreshAfter)))
                .toList();

        return new BoardPage(entries, nextCursor(slice));
    }

    private static BoardListings.BoardCursor cursorOf(BoardCursor cursor) {
        return cursor == null ? null
                : new BoardListings.BoardCursor(cursor.createdAt(), cursor.listingId());
    }

    /**
     * The adapter reports that another page exists; the anchor is the last row
     * it actually returned. An empty non-final slice would be an adapter bug,
     * so it produces no cursor instead of an exception in the read path.
     */
    private static BoardCursor nextCursor(BoardSlice slice) {
        if (!slice.hasMore() || slice.candidates().isEmpty()) {
            return null;
        }
        var last = slice.candidates().getLast().listing();
        return new BoardCursor(last.createdAt(), last.id());
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

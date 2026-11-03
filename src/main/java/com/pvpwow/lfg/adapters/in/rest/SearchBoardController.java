package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.SearchBoard;
import com.pvpwow.lfg.application.port.in.SearchBoard.SearchBoardQuery;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public board: ACTIVE listings for one region and bracket, newest first,
 * keyset-paginated.
 *
 * <p>{@code region} and {@code bracket} are mandatory query parameters, so a
 * missing one is Spring's own 400. Everything the caller can fix — an unknown
 * enum, an out-of-range limit — is a 422 carrying the reason, in line with the
 * error strategy.
 */
@RestController
@RequestMapping("/api/board")
public final class SearchBoardController {

    private final SearchBoard searchBoard;

    public SearchBoardController(SearchBoard searchBoard) {
        this.searchBoard = searchBoard;
    }

    @GetMapping
    BoardPageResponse search(
            @RequestParam String region,
            @RequestParam String bracket,
            @RequestParam(required = false) String wowClass,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer minItemLevel,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {

        SearchBoardQuery query = new SearchBoardQuery(
                WireParsing.parseEnum(Region.class, region),
                WireParsing.bracket(bracket),
                wowClass == null ? null : WireParsing.parseEnum(WowClass.class, wowClass),
                minRating == null ? null : new Rating(minRating),
                minItemLevel == null ? null : new ItemLevel(minItemLevel),
                cursor == null ? null : BoardCursorCodec.decode(cursor),
                pageSize(limit));

        return BoardPageResponse.from(searchBoard.search(query));
    }

    private static int pageSize(Integer limit) {
        if (limit == null) {
            return SearchBoard.DEFAULT_PAGE_SIZE;
        }
        if (limit < 1 || limit > SearchBoard.MAX_PAGE_SIZE) {
            throw new DomainRuleViolationException(
                    "limit must be between 1 and %d, got: %d"
                            .formatted(SearchBoard.MAX_PAGE_SIZE, limit));
        }
        return limit;
    }
}

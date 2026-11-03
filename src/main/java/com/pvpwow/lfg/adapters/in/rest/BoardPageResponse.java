package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.SearchBoard.BoardEntry;
import com.pvpwow.lfg.application.port.in.SearchBoard.BoardPage;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.TimeWindow;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The board page as the wire sees it: a deliberate projection, not a dump of
 * the aggregate.
 *
 * <ul>
 *   <li>The rating is the one for the queried bracket; other brackets are not
 *       the caller's business here.</li>
 *   <li>{@code ownerId} is absent — the board is public, the owner's identity
 *       is not needed to answer "who is available".</li>
 *   <li>Stale evidence is still sent, flagged, never hidden.</li>
 *   <li>Collections are sorted so the same board always serializes the same
 *       way.</li>
 * </ul>
 */
record BoardPageResponse(List<Item> items, String nextCursor) {

    static BoardPageResponse from(BoardPage page) {
        return new BoardPageResponse(
                page.entries().stream().map(Item::from).toList(),
                page.nextCursor() == null ? null : BoardCursorCodec.encode(page.nextCursor()));
    }

    record Item(
            UUID listingId,
            String bracket,
            Specialization spec,
            List<Language> languages,
            Schedule schedule,
            Instant createdAt,
            Instant expiresAt,
            CharacterSummary character) {

        static Item from(BoardEntry entry) {
            LfgListing listing = entry.listing();
            return new Item(
                    listing.id().value(),
                    WireParsing.bracketKey(listing.bracket()),
                    listing.spec(),
                    listing.languages().stream().sorted().toList(),
                    Schedule.from(listing.schedule()),
                    listing.createdAt(),
                    listing.expiresAt(),
                    CharacterSummary.from(entry));
        }
    }

    record Schedule(ZoneId zone, List<Window> windows) {

        static Schedule from(AvailabilitySchedule schedule) {
            return new Schedule(schedule.zone(), schedule.windows().stream()
                    .sorted(Comparator.comparing(TimeWindow::day)
                            .thenComparing(TimeWindow::start))
                    .map(window -> new Window(window.day(), window.start(), window.end()))
                    .toList());
        }
    }

    record Window(DayOfWeek day, LocalTime start, LocalTime end) {
    }

    record CharacterSummary(
            UUID id,
            String name,
            String realm,
            Region region,
            WowClass wowClass,
            List<Specialization> specs,
            int itemLevel,
            Instant gearObservedAt,
            boolean gearStale,
            Integer rating,
            Instant ratingObservedAt,
            boolean ratingStale) {

        static CharacterSummary from(BoardEntry entry) {
            Character character = entry.character();
            PvpStanding standing = character.standingFor(entry.listing().bracket()).orElse(null);
            return new CharacterSummary(
                    character.id().value(),
                    character.name().value(),
                    character.realm().value(),
                    character.region(),
                    character.wowClass(),
                    character.specs().stream().sorted().toList(),
                    character.itemLevel().value(),
                    character.gear().observedAt(),
                    entry.gearStale(),
                    standing == null ? null : standing.rating().value(),
                    standing == null ? null : standing.observedAt(),
                    entry.ratingStale());
        }
    }
}

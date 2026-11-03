package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.BoardListings.BoardSlice;
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
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The board query, in SQL. Filters run where PostgreSQL can use indexes: a
 * minimum CR is an {@code EXISTS} over the bracket's current-season standing
 * and a minimum item level is a predicate on freshly observed gear, so a
 * candidate with old evidence simply fails its filter instead of
 * contaminating the result set in Java.
 *
 * <p>Hydration is explicit and batched: three queries for the listing rows and
 * one per child collection, keyed by the ids the first query returned. A JPA
 * fetch graph over four collections would multiply rows and still need
 * deduplication; here every query has one obvious job.
 */
@Component
final class JdbcBoardListings implements BoardListings {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcBoardListings(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BoardSlice search(SearchCriteria criteria) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("region", criteria.region().name())
                .addValue("bracket", criteria.bracket().name())
                .addValue("now", Timestamp.from(criteria.now()))
                .addValue("evidenceFreshAfter", Timestamp.from(criteria.evidenceFreshAfter()))
                .addValue("fetchLimit", criteria.limit() + 1);

        if (criteria.cursor() != null) {
            parameters.addValue("cursorCreatedAt", Timestamp.from(criteria.cursor().createdAt()));
            parameters.addValue("cursorListingId", criteria.cursor().listingId().value());
        }

        List<BoardRow> rows = jdbc.query(
                boardSql(criteria, parameters), parameters, (rs, rowNumber) -> row(rs));

        // One extra row answers "is there another page?" without a second
        // count query, and the extra row is never hydrated or returned.
        boolean hasMore = rows.size() > criteria.limit();
        if (hasMore) {
            rows = rows.subList(0, criteria.limit());
        }
        if (rows.isEmpty()) {
            return new BoardSlice(List.of(), false);
        }

        List<UUID> listingIds = rows.stream().map(BoardRow::listingId).toList();
        List<UUID> characterIds = rows.stream().map(BoardRow::characterId).distinct().toList();

        Map<UUID, Set<Language>> languages = languages(listingIds);
        Map<UUID, Set<TimeWindow>> windows = windows(listingIds);
        Map<UUID, Set<Specialization>> specs = specs(characterIds);
        Map<UUID, Map<Bracket, PvpStanding>> standings = standings(characterIds);

        List<BoardCandidate> candidates = new ArrayList<>(rows.size());
        for (BoardRow row : rows) {
            candidates.add(new BoardCandidate(
                    listing(row, languages, windows),
                    character(row, specs, standings)));
        }
        return new BoardSlice(candidates, hasMore);
    }

    private static String boardSql(SearchCriteria criteria, MapSqlParameterSource parameters) {
        StringBuilder sql = new StringBuilder("""
                select l.id                as listing_id,
                       l.character_id,
                       l.bracket,
                       l.spec,
                       l.status,
                       l.availability_zone,
                       l.created_at,
                       l.expires_at,
                       c.owner_id,
                       c.name,
                       c.realm,
                       c.region,
                       c.wow_class,
                       c.item_level,
                       c.gear_observed_at
                  from lfg_listings l
                  join characters c on c.id = l.character_id
                 where l.status = 'ACTIVE'
                   and l.expires_at > :now
                   and c.region = :region
                   and l.bracket = :bracket
                """);

        if (criteria.wowClass() != null) {
            sql.append("   and c.wow_class = :wowClass\n");
            parameters.addValue("wowClass", criteria.wowClass().name());
        }
        if (criteria.minimumItemLevel() != null) {
            sql.append("   and c.item_level >= :minimumItemLevel\n");
            sql.append("   and c.gear_observed_at >= :evidenceFreshAfter\n");
            parameters.addValue("minimumItemLevel", criteria.minimumItemLevel().value());
        }
        if (criteria.minimumRating() != null) {
            sql.append("""
                       and exists (
                             select 1
                               from character_standings s
                              where s.character_id = c.id
                                and s.bracket = :bracket
                                and s.rating >= :minimumRating
                                and s.season_id = :currentSeason
                                and s.observed_at >= :evidenceFreshAfter
                       )
                    """);
            parameters.addValue("minimumRating", criteria.minimumRating().value());
            parameters.addValue("currentSeason", criteria.currentSeason().id());
        }

        if (criteria.cursor() != null) {
            // Row-value comparison, not two OR'd conditions: PostgreSQL turns
            // it into one index range on (created_at desc, id desc).
            sql.append("   and (l.created_at, l.id) < (:cursorCreatedAt, :cursorListingId)\n");
        }

        sql.append("""
                 order by l.created_at desc, l.id desc
                 limit :fetchLimit
                """);
        return sql.toString();
    }

    private Map<UUID, Set<Language>> languages(List<UUID> listingIds) {
        Map<UUID, Set<Language>> languages = new HashMap<>();
        jdbc.query("""
                        select listing_id, language
                          from listing_languages
                         where listing_id in (:listingIds)
                        """,
                Map.of("listingIds", listingIds),
                rs -> {
                    languages.computeIfAbsent(rs.getObject("listing_id", UUID.class),
                                    key -> new HashSet<>())
                            .add(ListingEntityMapper.language(rs.getString("language")));
                });
        return languages;
    }

    private Map<UUID, Set<TimeWindow>> windows(List<UUID> listingIds) {
        Map<UUID, Set<TimeWindow>> windows = new HashMap<>();
        jdbc.query("""
                        select listing_id, day_of_week, start_time, end_time
                          from listing_windows
                         where listing_id in (:listingIds)
                        """,
                Map.of("listingIds", listingIds),
                rs -> {
                    windows.computeIfAbsent(rs.getObject("listing_id", UUID.class),
                                    key -> new HashSet<>())
                            .add(new TimeWindow(
                                    DayOfWeek.valueOf(rs.getString("day_of_week")),
                                    time(rs, "start_time"),
                                    time(rs, "end_time")));
                });
        return windows;
    }

    private Map<UUID, Set<Specialization>> specs(List<UUID> characterIds) {
        Map<UUID, Set<Specialization>> specs = new HashMap<>();
        jdbc.query("""
                        select character_id, spec
                          from character_specs
                         where character_id in (:characterIds)
                        """,
                Map.of("characterIds", characterIds),
                rs -> {
                    specs.computeIfAbsent(rs.getObject("character_id", UUID.class),
                                    key -> new HashSet<>())
                            .add(Specialization.valueOf(rs.getString("spec")));
                });
        return specs;
    }

    private Map<UUID, Map<Bracket, PvpStanding>> standings(List<UUID> characterIds) {
        Map<UUID, Map<Bracket, PvpStanding>> standings = new HashMap<>();
        jdbc.query("""
                        select character_id, bracket, rating, season_id, observed_at
                          from character_standings
                         where character_id in (:characterIds)
                        """,
                Map.of("characterIds", characterIds),
                rs -> {
                    standings.computeIfAbsent(rs.getObject("character_id", UUID.class),
                                    key -> new HashMap<>())
                            .put(Bracket.valueOf(rs.getString("bracket")),
                                    new PvpStanding(
                                            new Rating(rs.getInt("rating")),
                                            new PvpSeason(rs.getInt("season_id")),
                                            instant(rs, "observed_at")));
                });
        return standings;
    }

    private static LfgListing listing(BoardRow row,
                                      Map<UUID, Set<Language>> languages,
                                      Map<UUID, Set<TimeWindow>> windows) {
        AvailabilitySchedule schedule = new AvailabilitySchedule(
                windows.getOrDefault(row.listingId(), Set.of()),
                ZoneId.of(row.scheduleZone()));
        return LfgListing.rehydrate(
                new ListingId(row.listingId()),
                new CharacterId(row.characterId()),
                row.bracket(),
                row.spec(),
                languages.getOrDefault(row.listingId(), Set.of()),
                schedule,
                row.status(),
                row.createdAt(),
                row.expiresAt());
    }

    private static Character character(BoardRow row,
                                       Map<UUID, Set<Specialization>> specs,
                                       Map<UUID, Map<Bracket, PvpStanding>> standings) {
        return Character.rehydrate(
                new CharacterId(row.characterId()),
                new PlayerId(row.ownerId()),
                new CharacterName(row.name()),
                new Realm(row.realm()),
                row.region(),
                row.wowClass(),
                new GearSnapshot(new ItemLevel(row.itemLevel()), row.gearObservedAt()),
                specs.getOrDefault(row.characterId(), Set.of()),
                standings.getOrDefault(row.characterId(), Map.of()));
    }

    private static BoardRow row(ResultSet rs) throws SQLException {
        return new BoardRow(
                rs.getObject("listing_id", UUID.class),
                rs.getObject("character_id", UUID.class),
                Bracket.valueOf(rs.getString("bracket")),
                Specialization.valueOf(rs.getString("spec")),
                ListingStatus.valueOf(rs.getString("status")),
                rs.getString("availability_zone"),
                instant(rs, "created_at"),
                instant(rs, "expires_at"),
                rs.getObject("owner_id", UUID.class),
                rs.getString("name"),
                rs.getString("realm"),
                Region.valueOf(rs.getString("region")),
                WowClass.valueOf(rs.getString("wow_class")),
                rs.getInt("item_level"),
                instant(rs, "gear_observed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static LocalTime time(ResultSet rs, String column) throws SQLException {
        return rs.getTime(column).toLocalTime();
    }

    private record BoardRow(
            UUID listingId,
            UUID characterId,
            Bracket bracket,
            Specialization spec,
            ListingStatus status,
            String scheduleZone,
            Instant createdAt,
            Instant expiresAt,
            UUID ownerId,
            String name,
            String realm,
            Region region,
            WowClass wowClass,
            int itemLevel,
            Instant gearObservedAt) {
    }
}

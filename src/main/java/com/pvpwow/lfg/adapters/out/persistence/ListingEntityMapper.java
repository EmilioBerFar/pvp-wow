package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.character.CharacterId;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableSet;

/** Total translation between the listing aggregate and its relational rows. */
final class ListingEntityMapper {

    private ListingEntityMapper() {
    }

    static ListingEntity toEntity(LfgListing listing, CharacterEntity character) {
        ListingEntity entity = new ListingEntity();
        entity.setId(listing.id().value());
        entity.setCharacter(character);
        entity.setBracket(listing.bracket());
        entity.setSpec(listing.spec());
        entity.setStatus(listing.status());
        entity.setScheduleZone(listing.schedule().zone().getId());
        entity.setCreatedAt(listing.createdAt());
        entity.setExpiresAt(listing.expiresAt());

        Set<String> languages = new HashSet<>();
        listing.languages().forEach(language -> languages.add(language.isoCode()));
        entity.setLanguages(languages);

        Set<WindowEmbeddable> windows = new HashSet<>();
        listing.schedule().windows().forEach(window -> windows.add(WindowEmbeddable.of(
                window.day(), window.start(), window.end())));
        entity.setWindows(windows);
        return entity;
    }

    static LfgListing toDomain(ListingEntity entity) {
        Set<Language> languages = entity.languages().stream()
                .map(ListingEntityMapper::language)
                .collect(toUnmodifiableSet());
        Set<com.pvpwow.lfg.domain.listing.TimeWindow> windows = entity.windows().stream()
                .map(window -> new com.pvpwow.lfg.domain.listing.TimeWindow(
                        window.dayOfWeek(), window.startTime(), window.endTime()))
                .collect(toUnmodifiableSet());
        AvailabilitySchedule schedule = new AvailabilitySchedule(windows,
                ZoneId.of(entity.scheduleZone()));

        return LfgListing.rehydrate(
                new ListingId(entity.id()),
                new CharacterId(entity.character().id()),
                entity.bracket(),
                entity.spec(),
                languages,
                schedule,
                entity.status(),
                entity.createdAt(),
                entity.expiresAt());
    }

    static Language language(String isoCode) {
        for (Language language : Language.values()) {
            if (language.isoCode().equals(isoCode)) {
                return language;
            }
        }
        throw new IllegalStateException("Unknown persisted language code: " + isoCode);
    }
}

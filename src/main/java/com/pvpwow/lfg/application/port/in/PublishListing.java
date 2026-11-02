package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.ListingId;

import java.util.Objects;
import java.util.Set;

/**
 * Driving port: publish one character's availability for one bracket.
 *
 * <p>The TTL is deliberately absent. Expiration is an application policy,
 * configured on the server, not a value a client can negotiate. Declared
 * facts arrive as domain objects because they are valid by construction.
 */
public interface PublishListing {

    ListingId publish(PublishListingCommand command);

    record PublishListingCommand(
            CharacterId characterId,
            Bracket bracket,
            Specialization spec,
            Set<Language> languages,
            AvailabilitySchedule schedule) {

        public PublishListingCommand {
            Objects.requireNonNull(characterId, "characterId must not be null");
            Objects.requireNonNull(bracket, "bracket must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
            Objects.requireNonNull(schedule, "schedule must not be null");
            // An omitted collection is client input, not a wiring bug. Let the
            // aggregate reject its emptiness with a domain rule violation.
            languages = languages == null ? Set.of() : Set.copyOf(languages);
        }
    }
}

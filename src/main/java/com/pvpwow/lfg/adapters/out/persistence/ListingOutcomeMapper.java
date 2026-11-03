package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;

import java.util.UUID;

/** Total translation between a listing outcome and its row. */
final class ListingOutcomeMapper {

    private ListingOutcomeMapper() {
    }

    static ListingOutcomeEntity toEntity(ListingOutcome outcome) {
        ListingOutcomeEntity entity = new ListingOutcomeEntity();
        entity.setListingId(outcome.listingId().value());
        entity.setCharacterId(outcome.characterId() == null
                ? null : outcome.characterId().value());
        entity.setBracket(outcome.bracket());
        entity.setSpec(outcome.spec());
        entity.setKind(outcome.kind());
        entity.setCreatedAt(outcome.createdAt());
        entity.setClosedAt(outcome.closedAt());
        return entity;
    }

    static ListingOutcome toDomain(ListingOutcomeEntity entity) {
        return new ListingOutcome(
                new com.pvpwow.lfg.domain.listing.ListingId(entity.listingId()),
                entity.characterId() == null ? null : new CharacterId(entity.characterId()),
                entity.bracket(),
                entity.spec(),
                entity.kind() == null ? ListingOutcomeKind.EXPIRED : entity.kind(),
                entity.createdAt(),
                entity.closedAt());
    }
}

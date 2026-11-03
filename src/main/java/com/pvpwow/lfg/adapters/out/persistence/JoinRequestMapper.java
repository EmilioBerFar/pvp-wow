package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.join.JoinRequestStatus;

import java.util.UUID;

/** Total translation between the join request aggregate and its row. */
final class JoinRequestMapper {

    private JoinRequestMapper() {
    }

    static JoinRequestEntity toEntity(JoinRequest request, UUID ownerId) {
        JoinRequestEntity entity = new JoinRequestEntity();
        entity.setId(request.id().value());
        entity.setRequesterId(request.requesterId().value());
        entity.setOwnerId(ownerId);
        entity.setListingId(request.listingId().value());
        entity.setStatus(request.status());
        entity.setCreatedAt(request.createdAt());
        entity.setExpiresAt(request.expiresAt());
        return entity;
    }

    static JoinRequest toDomain(JoinRequestEntity entity) {
        return JoinRequest.rehydrate(
                new JoinRequestId(entity.id()),
                new com.pvpwow.lfg.domain.character.CharacterId(entity.requesterId()),
                new com.pvpwow.lfg.domain.listing.ListingId(entity.listingId()),
                entity.status() == null ? JoinRequestStatus.PENDING : entity.status(),
                entity.createdAt(),
                entity.expiresAt());
    }
}

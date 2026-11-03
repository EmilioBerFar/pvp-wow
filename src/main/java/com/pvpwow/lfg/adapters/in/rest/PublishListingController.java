package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.PublishListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Publishes one listing.
 *
 * <p>201 carries the new id but no {@code Location}: this API does not expose a
 * read-by-id resource yet, and a Location header pointing at a 404 is a lie.
 * It arrives with that endpoint, not before.
 */
@RestController
@RequestMapping("/api/listings")
public final class PublishListingController {

    private final PublishListing publishListing;

    public PublishListingController(PublishListing publishListing) {
        this.publishListing = publishListing;
    }

    @PostMapping
    ResponseEntity<PublishedListingResponse> publish(
            @Valid @RequestBody PublishListingRequest request) {
        ListingId listingId = publishListing.publish(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PublishedListingResponse(listingId.value()));
    }

    record PublishedListingResponse(UUID id) {
    }
}

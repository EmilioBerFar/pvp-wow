package com.pvpwow.lfg.adapters;

import com.pvpwow.lfg.application.port.in.AcceptJoinRequest;
import com.pvpwow.lfg.application.port.in.GetCharacter;
import com.pvpwow.lfg.application.port.in.PublishListing;
import com.pvpwow.lfg.application.port.in.RegisterCharacter;
import com.pvpwow.lfg.application.port.in.RejectJoinRequest;
import com.pvpwow.lfg.application.port.in.RequestToJoin;
import com.pvpwow.lfg.application.port.in.SearchBoard;
import com.pvpwow.lfg.application.port.in.WithdrawJoinRequest;
import com.pvpwow.lfg.application.port.in.WithdrawListing;
import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.application.service.AcceptJoinRequestService;
import com.pvpwow.lfg.application.service.GetCharacterService;
import com.pvpwow.lfg.application.service.PublishListingService;
import com.pvpwow.lfg.application.service.RegisterCharacterService;
import com.pvpwow.lfg.application.service.RejectJoinRequestService;
import com.pvpwow.lfg.application.service.RequestToJoinService;
import com.pvpwow.lfg.application.service.SearchBoardService;
import com.pvpwow.lfg.application.service.WithdrawJoinRequestService;
import com.pvpwow.lfg.application.service.WithdrawListingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Composition root of the adapters: the ONLY place where a framework-free
 * application service becomes a Spring bean. It speaks PORTS, not adapter
 * classes — the wiring never names an implementation.
 */
@Configuration
public class WiringConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterCharacter registerCharacter(Characters characters,
                                        CurrentPlayerProvider currentPlayer,
                                        Seasons seasons,
                                        Clock clock) {
        return new RegisterCharacterService(characters, currentPlayer, seasons, clock);
    }

    @Bean
    GetCharacter getCharacter(Characters characters, CurrentPlayerProvider currentPlayer) {
        return new GetCharacterService(characters, currentPlayer);
    }

    @Bean
    PublishListing publishListing(Characters characters,
                                  Listings listings,
                                  CurrentPlayerProvider currentPlayer,
                                  Clock clock,
                                  @Value("${pvp.listing-ttl}") Duration listingTtl) {
        // Fail fast: a zero or negative TTL would publish listings that are
        // already due. A misconfiguration must kill the boot, not the first
        // request.
        if (listingTtl.isZero() || listingTtl.isNegative()) {
            throw new IllegalStateException(
                    "pvp.listing-ttl must be positive, got: " + listingTtl);
        }
        return new PublishListingService(characters, listings, currentPlayer, clock, listingTtl);
    }

    @Bean
    SearchBoard searchBoard(BoardListings boardListings, Seasons seasons, Clock clock) {
        return new SearchBoardService(boardListings, seasons, clock);
    }

    @Bean
    RequestToJoin requestToJoin(Characters characters, ListingLifecycle lifecycle,
                                CurrentPlayerProvider currentPlayer, Clock clock) {
        return new RequestToJoinService(characters, lifecycle, currentPlayer, clock);
    }

    @Bean
    AcceptJoinRequest acceptJoinRequest(Characters characters, ListingLifecycle lifecycle,
                                        CurrentPlayerProvider currentPlayer, Clock clock) {
        return new AcceptJoinRequestService(characters, lifecycle, currentPlayer, clock);
    }

    @Bean
    RejectJoinRequest rejectJoinRequest(Characters characters, ListingLifecycle lifecycle,
                                        CurrentPlayerProvider currentPlayer) {
        return new RejectJoinRequestService(characters, lifecycle, currentPlayer);
    }

    @Bean
    WithdrawJoinRequest withdrawJoinRequest(Characters characters, ListingLifecycle lifecycle,
                                            CurrentPlayerProvider currentPlayer) {
        return new WithdrawJoinRequestService(characters, lifecycle, currentPlayer);
    }

    @Bean
    WithdrawListing withdrawListing(Characters characters, ListingLifecycle lifecycle,
                                    CurrentPlayerProvider currentPlayer, Clock clock) {
        return new WithdrawListingService(characters, lifecycle, currentPlayer, clock);
    }
}

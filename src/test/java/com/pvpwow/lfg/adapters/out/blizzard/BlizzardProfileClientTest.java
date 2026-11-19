package com.pvpwow.lfg.adapters.out.blizzard;

import com.pvpwow.lfg.application.port.out.BattleNetCharacters.AccountCharacter;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterFacts;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterReference;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The Blizzard client, as a witness: it reports what the upstream said and
 * nothing else.
 *
 * <p>The upstream's JSON is the risky part of this unit - the shapes were
 * read from Blizzard's documentation, and a wrong guess here would show up
 * as an empty import much later. These tests pin the shapes against real
 * responses.
 */
class BlizzardProfileClientTest {

    private static final String HOST = "eu.api.blizzard.com";
    private static final String TOKEN = "the-access-token";

    private RestClient.Builder builder;
    private MockRestServiceServer blizzard;
    private BlizzardProfileClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        blizzard = MockRestServiceServer.bindTo(builder).build();
        client = new BlizzardProfileClient(builder, new FixedAccessToken(TOKEN), "");
        signIn();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAccountListingIsOneCallAndItAsksTheRegionNamespace() {
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/user/wow?namespace=profile-eu&locale=en_US"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {
                          "id": 1,
                          "wow_accounts": [
                            {
                              "id": 11,
                              "characters": [
                                {
                                  "id": 161996209,
                                  "name": "Strongem",
                                  "realm": {"id": 1306, "name": "Tarren Mill", "slug": "tarren-mill"},
                                  "playable_class": {"id": 8, "name": "Mage"},
                                  "playable_race": {"id": 1, "name": "Draenei"},
                                  "level": 70
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<AccountCharacter> characters = client.charactersIn(Region.EU);

        assertThat(characters).singleElement().satisfies(character -> {
            assertThat(character.blizzardId()).isEqualTo(161996209L);
            assertThat(character.name()).isEqualTo("Strongem");
            assertThat(character.realmSlug()).isEqualTo("tarren-mill");
            assertThat(character.className()).isEqualTo("Mage");
            assertThat(character.level()).isEqualTo(70);
        });
        blizzard.verify();
    }

    @Test
    void everyCharacterOfEveryWarcraftAccountIsReported() {
        blizzard.expect(requestTo("https://" + HOST + "/profile/user/wow"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {
                          "wow_accounts": [
                            {"id": 11, "characters": [
                              {"id": 1, "name": "One", "realm": {"slug": "tarren-mill"},
                               "playable_class": {"name": "Mage"}, "level": 70}
                            ]},
                            {"id": 12, "characters": [
                              {"id": 2, "name": "Two", "realm": {"slug": "kazzak"},
                               "playable_class": {"name": "Priest"}, "level": 80}
                            ]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.charactersIn(Region.EU))
                .extracting(AccountCharacter::name)
                .containsExactly("One", "Two");
    }

    @Test
    void anEmptyAccountIsAnEmptyListAndNotAFailure() {
        blizzard.expect(requestTo("https://" + HOST + "/profile/user/wow"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("{\"wow_accounts\": []}", MediaType.APPLICATION_JSON));

        assertThat(client.charactersIn(Region.EU)).isEmpty();
    }

    @Test
    void theFactsAreReadFromTheCharacterProfile() {
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {
                          "id": 161996209,
                          "name": "Strongem",
                          "character_class": {"id": 8, "name": "Mage"},
                          "active_spec": {"id": 63, "name": "Frost"},
                          "average_item_level": 690,
                          "equipped_item_level": 702,
                          "level": 70
                        }
                        """, MediaType.APPLICATION_JSON));
        // The summary lists NO brackets: this character has no arena record.
        // Asking for 2v2 and 3v3 would spend two calls to learn two 404s.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"honor_level": 42, "brackets": []}
                        """, MediaType.APPLICATION_JSON));

        Optional<CharacterFacts> facts = client.factsOf(reference());

        assertThat(facts).isPresent().get().satisfies(fact -> {
            assertThat(fact.className()).isEqualTo("Mage");
            assertThat(fact.specializationName()).isEqualTo("Frost");
            assertThat(fact.equippedItemLevel()).isEqualTo(702);
            assertThat(fact.ratings()).isEmpty();
        });
        blizzard.verify();
    }

    @Test
    void aCharacterWithNoArenaRecordCostsTwoCallsAndNotFour() {
        // The cost of talking to Blizzard is the whole reason the summary is
        // asked before the brackets: a character with no arena play costs two
        // calls, not four.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"}, "active_spec": {"name": "Frost"},
                         "equipped_item_level": 702}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"brackets": []}
                        """, MediaType.APPLICATION_JSON));

        client.factsOf(reference());

        // Exactly two requests: the profile and the summary. No bracket was
        // asked for, because the summary said there is none.
        blizzard.verify();
    }

    @Test
    void onlyTheBracketsTheSummaryReportsAreAskedFor() {
        // The summary says the character played 2v2 and not 3v3. Asking for
        // 3v3 would spend a call to learn a 404.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"}, "active_spec": {"name": "Frost"},
                         "equipped_item_level": 702}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"brackets": [
                          {"href": "https://eu.api.blizzard.com/profile/wow/character/tarren-mill/strongem/pvp-bracket/2v2"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-bracket/2v2"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"rating": 1800, "season": {"id": 43}}
                        """, MediaType.APPLICATION_JSON));

        Optional<CharacterFacts> facts = client.factsOf(reference());

        assertThat(facts).isPresent().get().satisfies(fact -> {
            assertThat(fact.ratings()).containsOnlyKeys(Bracket.TWO_VS_TWO);
            assertThat(fact.ratings().get(Bracket.TWO_VS_TWO).value()).isEqualTo(1800);
            assertThat(fact.ratings().get(Bracket.TWO_VS_TWO).season())
                    .isEqualTo(new PvpSeason(43));
        });
        blizzard.verify();
    }

    @Test
    void aBracketTheCharacterHasNotPlayedIsUnrankedAndNotAnError() {
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"}, "active_spec": {"name": "Frost"},
                         "equipped_item_level": 702}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"brackets": [
                          {"href": "https://eu.api.blizzard.com/profile/wow/character/tarren-mill/strongem/pvp-bracket/2v2"},
                          {"href": "https://eu.api.blizzard.com/profile/wow/character/tarren-mill/strongem/pvp-bracket/3v3"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-bracket/2v2"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"rating": 1800, "season": {"id": 43}}
                        """, MediaType.APPLICATION_JSON));
        // The 3v3 the summary listed but the character has not played.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-bracket/3v3"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withStatus(NOT_FOUND));

        Optional<CharacterFacts> facts = client.factsOf(reference());

        // The upstream's not-found and our domain's "unranked" mean the same
        // thing: no standing for that bracket.
        assertThat(facts).isPresent().get()
                .satisfies(fact -> assertThat(fact.ratings())
                        .containsOnlyKeys(Bracket.TWO_VS_TWO));
    }

    @Test
    void aCharacterThatCannotBeReadIsAnEmptyAnswer() {
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withStatus(NOT_FOUND));

        assertThat(client.factsOf(reference())).isEmpty();
    }

    @Test
    void eachRegionIsServedFromItsOwnHostAndNamespace() {
        blizzard.expect(requestTo("https://us.api.blizzard.com/profile/user/wow"
                        + "?namespace=profile-us&locale=en_US"))
                .andRespond(withSuccess("{\"wow_accounts\": []}", MediaType.APPLICATION_JSON));

        client.charactersIn(Region.US);

        blizzard.verify();
    }

    @Test
    void thePathSegmentsAreEncoded() {
        // A realm slug can carry an accent ("festung-der-stürme") and a name
        // could carry one too. An unencoded segment would change the path
        // instead of naming the character - and a slash inside a segment has
        // to stay escaped, or it would split the segment.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/festung-der-st%C3%BCrme/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"}, "active_spec": {"name": "Frost"},
                         "equipped_item_level": 702}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/festung-der-st%C3%BCrme/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("{\"brackets\": []}", MediaType.APPLICATION_JSON));

        client.factsOf(new CharacterReference(
                161996209L, "Strongem", "festung-der-stürme", Region.EU));

        blizzard.verify();
    }

    @Test
    void aSpaceInASegmentBecomesAPercentTwentyAndNotAPlus() {
        // URLEncoder is for FORM bodies: it turns a space into '+', which in
        // a path means a literal plus sign and not a space.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren%20mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"}, "active_spec": {"name": "Frost"},
                         "equipped_item_level": 702}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren%20mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("{\"brackets\": []}", MediaType.APPLICATION_JSON));

        client.factsOf(new CharacterReference(
                161996209L, "Strongem", "tarren mill", Region.EU));

        blizzard.verify();
    }

    @Test
    void aCharacterWithBothBracketsCostsFourCalls() {
        // The worst case, and it is bounded: profile, summary, and one call
        // per bracket the summary says exists.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"}, "active_spec": {"name": "Frost"},
                         "equipped_item_level": 702}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"brackets": [
                          {"href": "https://eu.api.blizzard.com/profile/wow/character/tarren-mill/strongem/pvp-bracket/2v2"},
                          {"href": "https://eu.api.blizzard.com/profile/wow/character/tarren-mill/strongem/pvp-bracket/3v3"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-bracket/2v2"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"rating": 1800, "season": {"id": 43}}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-bracket/3v3"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"rating": 2100, "season": {"id": 43}}
                        """, MediaType.APPLICATION_JSON));

        Optional<CharacterFacts> facts = client.factsOf(reference());

        assertThat(facts).isPresent().get().satisfies(fact -> {
            assertThat(fact.ratings()).containsOnlyKeys(Bracket.TWO_VS_TWO, Bracket.THREE_VS_THREE);
            assertThat(fact.ratings().get(Bracket.THREE_VS_THREE).value()).isEqualTo(2100);
        });
        blizzard.verify();
    }

    @Test
    void aCharacterWithoutAnActiveSpecializationStillReportsItsGearAndClass() {
        // The upstream omits active_spec for characters below level 10. The
        // port reports what IS there; the DOMAIN decides that a character
        // without a specialization cannot be registered. Dropping the
        // character here would be deciding on the upstream's behalf.
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("""
                        {"character_class": {"name": "Mage"},
                         "equipped_item_level": 120}
                        """, MediaType.APPLICATION_JSON));
        blizzard.expect(requestTo("https://" + HOST
                        + "/profile/wow/character/tarren-mill/strongem/pvp-summary"
                        + "?namespace=profile-eu&locale=en_US"))
                .andRespond(withSuccess("{\"brackets\": []}", MediaType.APPLICATION_JSON));

        Optional<CharacterFacts> facts = client.factsOf(reference());

        assertThat(facts).isPresent().get().satisfies(fact -> {
            assertThat(fact.className()).isEqualTo("Mage");
            assertThat(fact.equippedItemLevel()).isEqualTo(120);
            assertThat(fact.hasSpecialization()).isFalse();
        });
    }

    private CharacterReference reference() {
        return new CharacterReference(161996209L, "Strongem", "tarren-mill", Region.EU);
    }

    /**
     * A signed-in player, the way the filter chain leaves it.
     */
    private void signIn() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("battle-net")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(org.springframework.security.oauth2.core
                        .AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://oauth.battle.net/authorize")
                .tokenUri("https://oauth.battle.net/token")
                .userInfoUri("https://oauth.battle.net/userinfo")
                .userNameAttributeName("sub")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                        new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                                AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                                java.util.Map.of("sub", "123456789", "battletag", "emilio#12345"),
                                "sub"),
                        AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                        "battle-net"));
    }

    /**
     * A token provider that always answers the same thing, so the tests are
     * about the upstream's shapes and not about the session.
     */
    private record FixedAccessToken(String token) implements BattleNetAccessToken {

        @Override
        public String accessTokenOfCurrentPlayer() {
            return token;
        }
    }
}

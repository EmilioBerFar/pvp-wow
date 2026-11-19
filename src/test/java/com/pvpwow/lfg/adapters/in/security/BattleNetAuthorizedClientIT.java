package com.pvpwow.lfg.adapters.in.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The token lives in the database; the secret does not live anywhere it can
 * be read.
 *
 * <p>Two promises, and the second one is the reason this table exists at all:
 *
 * <ul>
 *   <li>The Battle.net access token is a ROW. It has to be: the product needs
 *       it to call the Blizzard API, and a token that lives in the heap of one
 *       process dies with that process. A player who logged in on Monday and
 *       imports on Tuesday, after a restart, must not get a 401 that says
 *       nothing about a missing token.</li>
 *   <li>The client SECRET is nowhere. Not in the session rows, not in this
 *       table. It lives in the {@code ClientRegistration} bean, in memory, and
 *       it is not written down.</li>
 * </ul>
 *
 * <p>WHY THIS TEST DOES NOT USE {@code oauth2Login()}: that post-processor
 * writes the authorized client into a MOCK repository
 * ({@code TestOAuth2AuthorizedClientRepository}), never into the real one and
 * never into the session. A test that signed in that way and then inspected
 * {@code spring_session_attributes} would find no secret - and would be
 * proving nothing at all, because the secret was never on that path. This one
 * drives the REAL repository, which is the only way the real behaviour can be
 * observed.
 */
@SpringBootTest
@Testcontainers
class BattleNetAuthorizedClientIT {

    private static final String REGISTRATION_ID = "battle-net";
    private static final String PRINCIPAL = "123456789";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void pointTheApplicationAtTheContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
            authorizedClientService;

    @Autowired
    private ClientRegistrationRepository registrations;

    @AfterEach
    void emptyTheDatabase() {
        jdbc.execute("delete from oauth2_authorized_client");
        jdbc.execute("delete from spring_session_attributes");
        jdbc.execute("delete from spring_session");
    }

    @Test
    void theAccessTokenIsStoredInTheDatabase() {
        saveAnAuthorizedClient("a-real-access-token");

        // The token is a ROW now, not an object in a map that a restart
        // empties.
        assertThat(jdbc.queryForObject(
                "select count(*) from oauth2_authorized_client", Integer.class)).isEqualTo(1);
    }

    @Test
    void theStoredTokenIsTheOneThatWasSaved() {
        saveAnAuthorizedClient("a-real-access-token");

        OAuth2AuthorizedClient loaded = authorizedClientService.loadAuthorizedClient(
                REGISTRATION_ID, PRINCIPAL);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("a-real-access-token");
    }

    @Test
    void theStoredTokenIsFoundByTheBattleNetAccountThatOwnsIt() {
        saveAnAuthorizedClient("a-real-access-token");

        // The key is the 'sub': the Battle.net account id. Stable for the life
        // of the account, and not a secret.
        assertThat(jdbc.queryForObject(
                "select principal_name from oauth2_authorized_client", String.class))
                .isEqualTo(PRINCIPAL);
    }

    @Test
    void noRowOfTheSessionCarriesTheClientSecret() {
        // The whole reason the authorized client is a TABLE and not a session
        // attribute: the session stores serialized objects, and a serialized
        // OAuth2AuthorizedClient carries the ClientRegistration - and with it
        // the secret - inside it.
        saveAnAuthorizedClient("a-real-access-token");

        assertThat(sessionAttributesContainingTheSecret()).isEmpty();
    }

    @Test
    void theDetectorWouldNoticeTheSecretIfSomebodyPutItThere() {
        // A test that cannot fail proves nothing. This one plants the secret
        // in a session row on purpose and checks the detector sees it, so the
        // assertion above is known to be a real one.
        storeASessionAttributeWithASecret();

        assertThat(sessionAttributesContainingTheSecret())
                .isNotEmpty()
                .allMatch(found -> found.contains("test-client-secret"));
    }

    @Test
    void theAuthorizedClientTableDoesNotCarryTheClientSecret() {
        saveAnAuthorizedClient("a-real-access-token");

        // The table stores the registration ID - a name - not the
        // registration itself. The secret has nowhere to hide in here.
        String secret = registrations.findByRegistrationId(REGISTRATION_ID).getClientSecret();

        assertThat(secret).isNotBlank();
        assertThat(rowsContaining(secret)).isEmpty();
    }

    @Test
    void theTokenCarriesItsScopesSoTheImportKnowsItMayCallBlizzard() {
        saveAnAuthorizedClient("a-real-access-token");

        assertThat(jdbc.queryForObject(
                "select access_token_scopes from oauth2_authorized_client", String.class))
                .contains("wow.profile");
    }

    /**
     * Saves a client through the REAL service, the way the login filter does.
     *
     * <p>The service's own API takes an {@code Authentication}; the
     * {@code AuthenticatedPrincipalOAuth2AuthorizedClientRepository} is what
     * turns one into a principal name, and the service is what this test
     * drives directly.
     */
    private void saveAnAuthorizedClient(String tokenValue) {
        ClientRegistration registration = registrations.findByRegistrationId(REGISTRATION_ID);
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Set.of("openid", "wow.profile"));

        authorizedClientService.saveAuthorizedClient(
                new OAuth2AuthorizedClient(registration, PRINCIPAL, token),
                new TestingAuthenticationToken(PRINCIPAL, "no-credentials-here"));
    }

    /**
     * Writes a session attribute that CONTAINS the secret, to prove the
     * assertion below can actually fail. A test that cannot fail proves
     * nothing.
     */
    private void storeASessionAttributeWithASecret() {
        String secret = registrations.findByRegistrationId(REGISTRATION_ID).getClientSecret();
        jdbc.update("insert into spring_session "
                        + "(primary_id, session_id, creation_time, last_access_time, "
                        + "max_inactive_interval, expiry_time) "
                        + "values (?, ?, ?, ?, ?, ?)",
                java.util.UUID.randomUUID().toString(),
                java.util.UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                3600,
                System.currentTimeMillis() + 3_600_000);
        jdbc.update("insert into spring_session_attributes "
                        + "(session_primary_id, attribute_name, attribute_bytes) values (?, ?, ?)",
                sessionPrimaryId(),
                "something.that.holds.a.secret",
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String sessionPrimaryId() {
        return jdbc.queryForObject("select primary_id from spring_session", String.class);
    }

    private List<String> sessionAttributesContainingTheSecret() {
        String secret = registrations.findByRegistrationId(REGISTRATION_ID).getClientSecret();
        return jdbc.query(
                "select attribute_bytes from spring_session_attributes",
                (rs, rowNum) -> new String(rs.getBytes(1), java.nio.charset.StandardCharsets.UTF_8))
                .stream()
                .filter(bytes -> bytes.contains(secret))
                .toList();
    }

    private List<String> rowsContaining(String secret) {
        List<String> found = new java.util.ArrayList<>();
        jdbc.query("select client_registration_id, principal_name, access_token_type, "
                        + "cast(access_token_value as text), access_token_scopes "
                        + "from oauth2_authorized_client",
                rs -> {
                    for (int column = 1; column <= 5; column++) {
                        String value = rs.getString(column);
                        if (value != null && value.contains(secret)) {
                            found.add(value);
                        }
                    }
                });
        return found;
    }
}

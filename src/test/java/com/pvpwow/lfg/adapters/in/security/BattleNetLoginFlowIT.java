package com.pvpwow.lfg.adapters.in.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REAL login path, end to end, and where the token lands.
 *
 * <p>This is the test that proves the promise of the other one. That one
 * drives the authorized-client service by hand; this one performs the whole
 * OAuth2 authorization-code dance against a stub Battle.net - the browser
 * hits the authorization endpoint, comes back with a code, the backend
 * exchanges it for a token - and then looks at the database.
 *
 * <p>Why that matters: the filter chain decides where the token goes, and
 * nothing short of running the filter chain proves anything about it. The
 * {@code oauth2Login()} test post-processor cannot help here: it writes into
 * a MOCK repository, so it would pass no matter what the real wiring does.
 *
 * <p>What it pins down:
 * <ul>
 *   <li>the token is a ROW in {@code oauth2_authorized_client} - it survives
 *       the restart that would otherwise lose it;</li>
 *   <li>the row is keyed by the Battle.net account ({@code sub});</li>
 *   <li>the client SECRET is in no row of {@code spring_session_attributes}
 *       and in no row of {@code oauth2_authorized_client}.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BattleNetLoginFlowIT {

    private static final String ACCESS_TOKEN = "the-access-token-battle-net-gave-us";
    private static final String CLIENT_SECRET = "test-client-secret";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    /**
     * The stub Battle.net: an authorization endpoint that hands out a code and
     * a token endpoint that exchanges it for a token.
     */
    static StubBattleNet battleNet;

    @BeforeAll
    static void startTheStubProvider() throws Exception {
        battleNet = new StubBattleNet(ACCESS_TOKEN);
    }

    @AfterAll
    static void stopTheStubProvider() {
        if (battleNet != null) {
            battleNet.close();
        }
    }

    @DynamicPropertySource
    static void pointTheApplicationAtTheStubs(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.security.oauth2.client.provider.battle-net.authorization-uri",
                () -> battleNet.baseUrl() + "/authorize");
        registry.add("spring.security.oauth2.client.provider.battle-net.token-uri",
                () -> battleNet.baseUrl() + "/token");
        registry.add("spring.security.oauth2.client.provider.battle-net.user-info-uri",
                () -> battleNet.baseUrl() + "/userinfo");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheDatabase() {
        jdbc.execute("delete from oauth2_authorized_client");
        jdbc.execute("delete from spring_session_attributes");
        jdbc.execute("delete from spring_session");
        jdbc.execute("delete from characters");
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from players");
    }

    @Test
    void theLoginLeavesTheAccessTokenInTheDatabase() throws Exception {
        completeTheLogin();

        // The token is a ROW. It has to be: the WU19 import needs it days
        // after the login, and a token in the heap of one process would be
        // gone by then.
        assertThat(jdbc.queryForObject(
                "select count(*) from oauth2_authorized_client", Integer.class))
                .isEqualTo(1);
        // The service stores the token through a LobHandler as plain UTF-8
        // bytes, so reading the bytea back gives the token itself. This is
        // how the next instance - or the WU19 import - reads it.
        assertThat(new String(jdbc.queryForObject(
                "select access_token_value from oauth2_authorized_client", byte[].class),
                java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void theTokenRowIsKeyedByTheBattleNetAccount() throws Exception {
        completeTheLogin();

        // The 'sub' is the account id: stable for the life of the account,
        // and not a secret.
        assertThat(jdbc.queryForObject(
                "select principal_name from oauth2_authorized_client", String.class))
                .isEqualTo("123456789");
    }

    @Test
    void theClientSecretIsInNoRowOfTheSession() throws Exception {
        completeTheLogin();

        assertThat(sessionAttributesContainingTheSecret()).isEmpty();
    }

    @Test
    void theClientSecretIsInNoRowOfTheAuthorizedClientTable() throws Exception {
        completeTheLogin();

        assertThat(rowsContainingTheSecret()).isEmpty();
    }

    /**
     * Walks the browser through the real dance: the application redirects the
     * browser to Battle.net, Battle.net sends it back with a code, and the
     * callback exchanges the code for a token.
     *
     * <p>The browser is not simulated: the redirect the application produces
     * is read, the stub is called the way a browser would call it, and the
     * code it returns is handed back to the application. The state and the
     * PKCE challenge travel in the first redirect, so the callback is the
     * REAL one - the same URL the browser would have reached.
     */
    private void completeTheLogin() throws Exception {
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/battle-net"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // The session holds the authorization request - the state and the PKCE
        // verifier - so the callback must arrive with the SAME session. A
        // browser keeps the cookie; MockMvc does not, so it is carried here
        // by hand.
        jakarta.servlet.http.Cookie sessionCookie = null;
        for (jakarta.servlet.http.Cookie cookie : authorization.getResponse().getCookies()) {
            if ("SESSION".equals(cookie.getName())) {
                sessionCookie = cookie;
            }
        }
        assertThat(sessionCookie).as("the authorization request lives in the session")
                .isNotNull();

        // The URL the browser would follow. The stub answers it with the
        // redirect that carries the authorization code back.
        String authorizationUrl = authorization.getResponse().getRedirectedUrl();
        assertThat(authorizationUrl).contains(battleNet.baseUrl() + "/authorize");

        java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                new java.net.URL(authorizationUrl).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.connect();
        String callbackUrl = connection.getHeaderField("Location");
        assertThat(callbackUrl).contains("code=the-code");

        // The callback: the code comes back and the backend exchanges it for
        // a token. This is where the filter chain saves the authorized
        // client.
        MvcResult callback = mockMvc.perform(get(callbackUrl).cookie(sessionCookie))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // A failed login redirects to /login?error. A successful one lands on
        // the application.
        assertThat(callback.getResponse().getRedirectedUrl())
                .as("a failed login would redirect to /login?error")
                .doesNotContain("error");
    }

    private List<String> sessionAttributesContainingTheSecret() {
        return jdbc.query(
                "select attribute_bytes from spring_session_attributes",
                (rs, rowNum) -> new String(rs.getBytes(1),
                        java.nio.charset.StandardCharsets.UTF_8))
                .stream()
                .filter(bytes -> bytes.contains(CLIENT_SECRET))
                .toList();
    }

    private List<String> rowsContainingTheSecret() {
        List<String> found = new ArrayList<>();
        jdbc.query("select client_registration_id, principal_name, access_token_type, "
                        + "cast(access_token_value as text), access_token_scopes "
                        + "from oauth2_authorized_client",
                rs -> {
                    for (int column = 1; column <= 5; column++) {
                        String value = rs.getString(column);
                        if (value != null && value.contains(CLIENT_SECRET)) {
                            found.add(value);
                        }
                    }
                });
        return found;
    }
}

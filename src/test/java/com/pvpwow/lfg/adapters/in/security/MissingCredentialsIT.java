package com.pvpwow.lfg.adapters.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application does not start without a Battle.net client.
 *
 * <p>WU18 shipped with a placeholder - {@code not-configured} - so the
 * application would start and the LOGIN would fail. That was the wrong way
 * round: the operator sees a broken login and has to guess that the client
 * was never configured, while the application looks perfectly healthy. An
 * application that cannot authenticate should not pretend to be running.
 *
 * <p>Why a real application start and not a slice: the failure happens while
 * the client registration is being validated, which only the full
 * auto-configuration does. A slice would not notice. And why a real
 * database: the boot must fail for the credentials and for NOTHING ELSE, or
 * the test would pass for the wrong reason.
 *
 * <p>Note there is no {@code @SpringBootTest} here: this test starts the
 * application BY HAND, because that is the thing being tested. A test context
 * would already be running and would prove nothing.
 */
@Testcontainers
class MissingCredentialsIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Test
    void theApplicationDoesNotStartWithoutTheClientId() {
        assertThatThrownBy(() -> startWith(clientIdMissing()))
                .as("an application that cannot authenticate must not pretend to run")
                .rootCause()
                .hasMessageContaining("BATTLE_NET_CLIENT_ID");
    }

    @Test
    void theApplicationDoesNotStartWithoutTheClientSecret() {
        assertThatThrownBy(() -> startWith(clientSecretMissing()))
                .as("a client id without a secret cannot complete the handshake")
                .rootCause()
                .hasMessageContaining("BATTLE_NET_CLIENT_SECRET");
    }

    /**
     * The properties of a healthy application, minus one credential.
     *
     * <p>The missing one is defined as the UNRESOLVED PLACEHOLDER, not
     * removed: removing it from this map would simply let the test
     * classpath's own credentials fill the gap, and the application would
     * start perfectly well. This is what a deployment without the variable
     * looks like once Spring has resolved what it could.
     */
    private Map<String, Object> clientIdMissing() {
        Map<String, Object> properties = healthy();
        properties.put("spring.security.oauth2.client.registration.battle-net.client-id",
                "${BATTLE_NET_CLIENT_ID}");
        return properties;
    }

    private Map<String, Object> clientSecretMissing() {
        Map<String, Object> properties = healthy();
        properties.put("spring.security.oauth2.client.registration.battle-net.client-secret",
                "${BATTLE_NET_CLIENT_SECRET}");
        return properties;
    }

    private Map<String, Object> healthy() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        properties.put("spring.security.oauth2.client.registration.battle-net.client-id",
                "test-client-id");
        properties.put("spring.security.oauth2.client.registration.battle-net.client-secret",
                "test-client-secret");
        return properties;
    }

    /**
     * Starts the application with the given properties.
     *
     * <p>The properties are added as a property source with the HIGHEST
     * priority, because the alternatives do not work:
     * {@code SpringApplicationBuilder.properties()} and
     * {@code setDefaultProperties()} both have LOWER priority than
     * application.yml, so the application would try to reach localhost:5432
     * and fail for the wrong reason. A test that fails for the wrong reason
     * is worse than no test.
     */
    private void startWith(Map<String, Object> properties) {
        SpringApplication application = new SpringApplication(
                com.pvpwow.lfg.PvpWowApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(context -> context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("this-test", properties)));
        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.isRunning())
                    .as("the application should not have started at all")
                    .isFalse();
        }
    }
}

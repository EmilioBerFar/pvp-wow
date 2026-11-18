package com.pvpwow.lfg.adapters.in.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Battle.net client must be configured, and the application must SAY SO at
 * startup.
 *
 * <p>Why this exists, when Spring could have done it: because it did not.
 * Spring Boot resolves a missing {@code ${BATTLE_NET_CLIENT_ID}} by leaving
 * the placeholder TEXT in place - {@code resolvePlaceholders}, not
 * {@code resolveRequiredPlaceholders} - so the client registration is built
 * with a literal {@code ${BATTLE_NET_CLIENT_ID}} as its id. The application
 * starts, looks perfectly healthy, and fails at the LOGIN with an error that
 * says nothing about a missing variable. The operator has to guess.
 *
 * <p>An application that cannot authenticate should not pretend to be
 * running, so the check is explicit and happens at the boot. The message
 * names the variable, because the operator's next action is to set it.
 *
 * <p>It lives here, next to the filter chain that uses those credentials, and
 * not in the YAML: a YAML default cannot fail loudly. A default of
 * {@code not-configured} is a value that LOOKS like a credential and is not
 * one, and that is exactly the kind of failure this class exists to prevent.
 */
@Configuration(proxyBeanMethods = false)
final class BattleNetClientConfiguration {

    /**
     * Fails the boot when the client is not configured.
     */
    @Bean
    BattleNetClientConfigured battleNetClientIsConfigured(
            @Value("${spring.security.oauth2.client.registration.battle-net.client-id}")
            String clientId,
            @Value("${spring.security.oauth2.client.registration.battle-net.client-secret}")
            String clientSecret) {
        requireReal(clientId, "BATTLE_NET_CLIENT_ID");
        requireReal(clientSecret, "BATTLE_NET_CLIENT_SECRET");
        return new BattleNetClientConfigured();
    }

    /**
     * A value that is missing, empty, or still the unresolved placeholder is
     * not a credential.
     */
    private static void requireReal(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    variable + " is not configured: the Battle.net client id and secret "
                            + "must be set as environment variables, or the application "
                            + "cannot authenticate anybody");
        }
        if (value.contains("${")) {
            throw new IllegalStateException(
                    variable + " is not configured: the value still looks like an "
                            + "unresolved placeholder ('" + value + "'). Set the "
                            + "environment variable and restart");
        }
    }

    /**
     * The marker bean. Its only job is to exist when the client is properly
     * configured: if it is missing, the boot failed, and that is the point.
     */
    record BattleNetClientConfigured() {
    }
}

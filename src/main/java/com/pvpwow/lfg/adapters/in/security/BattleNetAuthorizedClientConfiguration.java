package com.pvpwow.lfg.adapters.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * The authorized client stops living in the heap of one process.
 *
 * <p>WHY THIS EXISTS: Spring Security's default
 * {@code OAuth2AuthorizedClientService} is {@code InMemoryOAuth2AuthorizedClientService},
 * so the Battle.net access token dies with the JVM. While the only thing the
 * token was used for was the login handshake, that was invisible: the login
 * happened seconds before the token was needed. The moment the product needs
 * the token for something else - the WU19 import calls the Blizzard profile
 * API with it - the difference becomes a bug nobody can explain: the player
 * logged in three days ago, the process was restarted since, and the import
 * fails with a 401 that says nothing about a missing token.
 *
 * <p>So the token is a ROW, in the same database that already holds the
 * session. A restart no longer logs the player out of the ability to import,
 * and a second instance sees the tokens of the first.
 *
 * <p>WHY THIS IS ALSO THE RIGHT PLACE FOR THE CLIENT SECRET: the table
 * stores the registration ID and the tokens - NOT the {@code ClientRegistration}
 * itself, which is where the secret lives. The secret therefore never leaves
 * the bean, and it never reaches a row anybody can read. The session could
 * not make that promise: it stores serialized objects, and a serialized
 * {@code OAuth2AuthorizedClient} carries the {@code ClientRegistration} - and
 * with it the secret - inside it.
 *
 * <p>Nothing else changes: Boot already registers an
 * {@code AuthenticatedPrincipalOAuth2AuthorizedClientRepository} that
 * delegates to whatever {@code OAuth2AuthorizedClientService} it finds, and
 * this bean replaces the in-memory one. The repository keeps working exactly
 * as before; the bytes just land in PostgreSQL now.
 */
@Configuration(proxyBeanMethods = false)
final class BattleNetAuthorizedClientConfiguration {

    /**
     * The authorized client, in the database.
     *
     * <p>The service reads and writes exactly nine columns and never the
     * {@code ClientRegistration}, so the secret cannot reach the table. The
     * principal name is the Battle.net {@code sub} - the account id - because
     * that is what {@code authentication.getName()} returns for an OAuth2
     * login, and it is stable for the life of the account.
     */
    @Bean
    JdbcOAuth2AuthorizedClientService authorizedClientService(
            JdbcOperations jdbc,
            ClientRegistrationRepository registrations) {
        return new JdbcOAuth2AuthorizedClientService(jdbc, registrations);
    }
}

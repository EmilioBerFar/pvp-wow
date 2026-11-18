package com.pvpwow.lfg.adapters.in.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The filter chain: who may call what.
 *
 * <p>Only ONE endpoint is public, and it is public on purpose: the board. It
 * is the product, it shows nothing private, and requiring a login to look at
 * it would mean requiring a Battle.net account to see whether the board is
 * worth signing into.
 *
 * <p>Everything else needs a session, which is what the {@code oauth2Login}
 * filter establishes. There is no form login and no basic auth: the only
 * password in this product belongs to Battle.net, and duplicating it here
 * would be inventing a second identity system.
 *
 * <p>CSRF is disabled, and that is a decision rather than an oversight: the
 * session cookie is {@code SameSite=Lax} (see application.yml), so a
 * cross-site POST arrives without it and is anonymous; no state-changing
 * route is a GET, so the Lax hole (top-level GET navigation) has nothing to
 * exploit. When WU20 gives the SPA a separate origin, this is the line to
 * revisit - at that point the cookie becomes cross-site and CSRF comes back.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
final class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http,
                            BattleNetSignInHandler signIn,
                            ObjectMapper json) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        // The board, and only the board.
                        .requestMatchers(HttpMethod.GET, "/api/board").permitAll()
                        // The login handshake itself, plus the container's
                        // error dispatch, which must not bounce through the
                        // entry point or a 404 would become a 401.
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login.successHandler(signIn))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(failure -> failure
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(json)))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}

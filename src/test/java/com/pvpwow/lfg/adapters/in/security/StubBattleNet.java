package com.pvpwow.lfg.adapters.in.security;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A stub Battle.net, built on the JDK's own HTTP server.
 *
 * <p>The alternative was WireMock, and the only thing it would have brought
 * was a dependency: this stub has to answer three requests, and the JDK has
 * an HTTP server in it. A test that needs a library to prove where a token
 * lands is a test with one more thing that can break.
 *
 * <p>It answers:
 * <ul>
 *   <li>{@code GET /authorize} - the browser is sent back to the application
 *       with an authorization code;</li>
 *   <li>{@code POST /token} - the code is exchanged for an access token;</li>
 *   <li>{@code GET /userinfo} - the provider reports who the caller is.</li>
 * </ul>
 */
final class StubBattleNet implements AutoCloseable {

    private final HttpServer server;
    private final String accessToken;

    StubBattleNet(String accessToken) throws IOException {
        this.accessToken = accessToken;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/authorize", exchange -> {
            // The provider sends the browser back with the authorization code
            // AND the state it was given. The state is what links the
            // callback to the saved authorization request - without it the
            // application cannot find the PKCE verifier and the login fails.
            //
            // The state is DECODED before it goes back. A real browser
            // decodes the query string when it reads the parameter, and
            // MockMvc does not: it hands the raw query through. Sending it
            // decoded makes the stub behave like the browser, and the
            // application then compares like with like.
            String rawState = null;
            for (String parameter : exchange.getRequestURI().getRawQuery().split("&")) {
                if (parameter.startsWith("state=")) {
                    rawState = java.net.URLDecoder.decode(
                            parameter.substring("state=".length()), StandardCharsets.UTF_8);
                }
            }
            String location = "/login/oauth2/code/battle-net?code=the-code"
                    + (rawState == null ? "" : "&state=" + rawState);
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/token", exchange -> {
            String body = """
                    {
                      "access_token": "%s",
                      "token_type": "bearer",
                      "expires_in": 3600,
                      "scope": "wow.profile"
                    }
                    """.formatted(this.accessToken);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.createContext("/userinfo", exchange -> {
            String body = """
                    {"sub": "123456789", "battletag": "emilio#12345"}
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

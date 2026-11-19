package com.pvpwow.lfg.adapters.out.blizzard;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A stub Blizzard profile API, built on the JDK's own HTTP server.
 *
 * <p>Its whole job is to answer like the upstream does, so that an
 * integration test can exercise the import without a network round trip to a
 * third party - and without anybody's Battle.net account. The rules the
 * stub keeps are the ones the real API has:
 *
 * <ul>
 *   <li>the account listing lives at {@code /profile/user/wow};</li>
 *   <li>a character lives at {@code /profile/wow/character/{slug}/{name}};</li>
 *   <li>the PvP summary lives at {@code .../pvp-summary} and lists only the
 *       brackets the character HAS rated;</li>
 *   <li>a bracket lives at {@code .../pvp-bracket/{bracket}};</li>
 *   <li>a bracket the character has not played answers 404.</li>
 * </ul>
 */
public final class StubBlizzard implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> bodies = new HashMap<>();
    private final java.util.List<AccountCharacter> accountCharacters = new java.util.ArrayList<>();

    public StubBlizzard() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = bodies.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    /**
     * The base URL the application should call.
     */
    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /**
     * Teaches the stub what to answer for a path.
     */
    public StubBlizzard answers(String path, String body) {
        bodies.put(path, body);
        return this;
    }

    /**
     * The account listing: the characters of the account.
     *
     * <p>It ADDS a character to the listing rather than replacing it, because
     * an account usually holds more than one - and the listing is what the
     * import checks the client's choices against.
     */
    public StubBlizzard withAccount(long characterId, String name, String realmSlug,
                              String className, int level) {
        accountCharacters.add(new AccountCharacter(characterId, name, realmSlug, className, level));
        return answers("/profile/user/wow", accountListing());
    }

    /**
     * The account listing, as the upstream sends it.
     */
    private String accountListing() {
        StringBuilder characters = new StringBuilder();
        for (AccountCharacter character : accountCharacters) {
            characters.append("""
                    {
                      "id": %d,
                      "name": "%s",
                      "realm": {"id": 1306, "name": "Tarren Mill", "slug": "%s"},
                      "playable_class": {"id": 8, "name": "%s"},
                      "level": %d
                    },
                    """.formatted(character.id(), character.name(), character.realmSlug(),
                    character.className(), character.level()));
        }
        return """
                {
                  "id": 1,
                  "wow_accounts": [
                    {
                      "id": 11,
                      "characters": [%s]
                    }
                  ]
                }
                """.formatted(characters.toString().replaceAll(",\\s*$", ""));
    }

    /**
     * One character of the account, as the listing reports it.
     */
    private record AccountCharacter(long id, String name, String realmSlug,
                                    String className, int level) {
    }

    /**
     * One character's own profile: its class, its gear and its active spec.
     */
    public StubBlizzard withCharacter(String realmSlug, String name, String className,
                               String specialization, int equippedItemLevel) {
        String path = "/profile/wow/character/" + realmSlug + "/" + name.toLowerCase();
        String activeSpec = specialization == null ? "" : """
                , "active_spec": {"id": 63, "name": "%s"}""".formatted(specialization);
        return answers(path, """
                {
                  "id": 1,
                  "name": "%s",
                  "character_class": {"id": 8, "name": "%s"}%s,
                  "average_item_level": %d,
                  "equipped_item_level": %d,
                  "level": 70
                }
                """.formatted(name, className, activeSpec, equippedItemLevel - 12,
                equippedItemLevel));
    }

    /**
     * The PvP summary: which brackets the character HAS rated.
     */
    public StubBlizzard withSummary(String realmSlug, String name, String... brackets) {
        String path = "/profile/wow/character/" + realmSlug + "/" + name.toLowerCase()
                + "/pvp-summary";
        StringBuilder links = new StringBuilder();
        for (String bracket : brackets) {
            links.append("""
                    {"href": "https://eu.api.blizzard.com/profile/wow/character/%s/%s/pvp-bracket/%s"},
                    """.formatted(realmSlug, name.toLowerCase(), bracket));
        }
        String body = brackets.length == 0
                ? """
                {"honor_level": 0, "brackets": []}
                """
                : """
                {"honor_level": 42, "brackets": [%s]}
                """.formatted(links.toString().replaceAll(",\\s*$", ""));
        return answers(path, body);
    }

    /**
     * One bracket's rating, with the season it belongs to.
     */
    public StubBlizzard withBracket(String realmSlug, String name, String bracket,
                             int rating, int seasonId) {
        String path = "/profile/wow/character/" + realmSlug + "/" + name.toLowerCase()
                + "/pvp-bracket/" + bracket;
        return answers(path, """
                {"rating": %d, "season": {"id": %d}}
                """.formatted(rating, seasonId));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

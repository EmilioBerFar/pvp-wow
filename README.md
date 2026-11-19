# pvp-wow

**LFG board for World of Warcraft arena PvP.** Players import their characters
from Battle.net, publish their availability for a bracket (2v2 / 3v3), and
other players ask to join them. When both sides agree, the search ends and the
listing is recorded as completed.

Every rating on the board was **observed**, not claimed: there is no way to
insert a character by hand, so the numbers other players filter by are the
numbers the game reported.

Built as a learning project for **hexagonal architecture** on Spring Boot and
PostgreSQL. Every product decision is argued in
[`docs/domain-spec.md`](docs/domain-spec.md), with the reason it was taken —
that document is the real content of this repository; the code is its
consequence.

---

## Why it looks like this

The interesting part of this project is not the LFG board. It is that **every
decision that could have gone another way has a written reason**, and that
the reason lives next to the code that implements it.

Some of those decisions were reversed along the way, and the reversals are
in the spec too. That is the point: the process is the lesson.

---

## Running it

Requirements: **JDK 25** (Temurin) and **Docker** (for PostgreSQL).

```bash
docker compose up -d        # PostgreSQL 18 on :5432
./mvnw verify               # build + unit tests + integration tests
./mvnw spring-boot:run      # the API on :8080
```

There is **no dev mode**. Authentication is Battle.net, always: a player exists
because somebody logged in, and the `sub` claim is what says who. There is no
header that names a player, no fixture endpoint that creates one, and no way
into the application that is not a real Battle.net account — because a
deployed artifact with a "development only" door is one environment variable
away from production.

The application needs real OAuth2 credentials to start:

```bash
export BATTLE_NET_CLIENT_ID=...        # https://develop.battle.net
export BATTLE_NET_CLIENT_SECRET=...
./mvnw spring-boot:run
```

Register the redirect URI `{baseUrl}/login/oauth2/code/battle-net` in the
Battle.net client, or the provider will refuse the callback.

**Without those two variables the application does not start.** That is
deliberate: an application that cannot authenticate should not pretend to be
running. The boot fails naming the variable that is missing, instead of
letting the operator discover it later as a broken login.

The session cookie is `Secure` by default, because the session *is* the
credential: a browser refuses to send it over plain HTTP. That means local
development over `http://localhost` needs one extra variable:

```bash
export SESSION_COOKIE_SECURE=false     # only for local http:// development
```

Sessions live in PostgreSQL, not in the heap of the server, so a restart does
not log everybody out and a second instance sees the sessions of the first.

`mvnw verify` is the whole test suite: it runs the unit tests (fast, no
Docker) and then the integration tests against a **real PostgreSQL** in a
throwaway Testcontainers instance. The subject under test in the persistence
layer *is* the database — its partial indexes, its cascades, its row-value
comparisons — so H2 in PostgreSQL mode would pass and lie.

`JAVA_HOME` on PowerShell, once:

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME',
  'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot', 'User')
```

Database credentials come from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` and
default to the values in `docker-compose.yml`. Nothing secret is committed.

### The API

Signing in is a browser round trip, not a `curl` call: Battle.net redirects
the browser, the callback exchanges the code for a token, and the answer is a
session cookie. `curl` can join in once that cookie exists.

```bash
# Sign in: the browser goes to Battle.net and comes back with a session cookie.
# Copy the cookie out of the browser's storage and put it in cookies.txt.
open localhost:8080/oauth2/authorization/battle-net

# Who am I
curl -b cookies.txt localhost:8080/api/players/me
# → 200 {"id":"<player-uuid>","battleTag":"emilio#12345"}

# The characters of your Battle.net account (one region at a time)
curl 'localhost:8080/api/characters/importable?region=EU' -b cookies.txt
# → 200 [{"blizzardId":161996209,"name":"Strongem","realmSlug":"tarren-mill",
#          "className":"Mage","level":70}]

# Import the ones you choose. There is no other way to create a character:
# every rating on the board was observed, not claimed. Note that the client
# sends ONLY the id: the server checks it against your account, so nobody can
# make it import somebody else's character.
curl -X POST 'localhost:8080/api/characters/import?region=EU' -b cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"characters":[{"blizzardId":161996209}]}'
# → 200 {"imported":1,"refreshed":0,"skipped":0,"rejected":0,
#         "outcomes":[{"name":"Strongem","realmSlug":"tarren-mill",
#                      "outcome":"IMPORTED"}]}

# Publish availability
curl -X POST localhost:8080/api/listings -b cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"characterId":"<uuid>","bracket":"3v3","spec":"MAGE_FROST",
       "languages":["ES"],
       "schedule":{"zone":"Europe/Madrid",
                   "windows":[{"day":"MONDAY","start":"18:00","end":"22:00"}]}}'

# Read the board (newest first, keyset paginated) — no session needed
curl 'localhost:8080/api/board?region=EU&bracket=3v3&minRating=1800&limit=20'

# Ask to join, and the owner's answer
curl -X POST localhost:8080/api/listings/<listing-id>/join-requests -b cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"characterId":"<uuid>"}'
curl -X POST localhost:8080/api/join-requests/<id>/accept -b cookies.txt

# Log out: the session, and with it the identity, is gone
curl -X POST localhost:8080/api/auth/logout -b cookies.txt
```

The board is the only public endpoint. Everything else answers an unauthenticated
call with a 401 `application/problem+json`, never a redirect: a `fetch()` that
follows a redirect would get an HTML login page instead of a status it can read.

---

## Architecture

```
                    ┌──────────────────────────────────────┐
                    │            adapters.in.rest          │
                    │  controllers · request/response DTOs │
                    │  · the advice that maps failures      │
                    └──────────────────┬───────────────────┘
                                       │  driving ports
                    ┌──────────────────▼───────────────────┐
                    │           application               │
                    │  use cases · services · ports        │
                    │  (port.in = what it does,            │
                    │   port.out = what it needs)          │
                    └──────────────────┬───────────────────┘
                                       │  driven ports
       ┌───────────────────────────────┼───────────────────────────────┐
       │                               │                               │
┌──────▼──────┐              ┌─────────▼────────┐            ┌─────────▼────────┐
│   domain    │              │ persistence (out) │            │ security (out)   │
│ aggregates  │              │ JPA · JdbcTemplate│            │ current player   │
│ value objs  │              │ · Flyway          │            │ season · synergy │
│ domain rules│              └──────────────────┘            └──────────────────┘
└─────────────┘
```

The rule that makes it hexagonal: **dependencies point inward**. The domain
has zero framework imports, and that is not a convention — it is an
[ArchUnit](https://www.archunit.org/) rule that fails the build, plus a
purity check you can run by hand:

```powershell
Get-ChildItem -Recurse src\main\java\com\pvpwow\lfg\domain -Filter *.java |
  Select-String -Pattern 'import\s+(org\.|jakarta\.|com\.fasterxml)'
```

It prints nothing. It must print nothing.

```
com.pvpwow.lfg
├── domain          pure model: aggregates, value objects, domain rules
│   ├── character   Character, GearSnapshot, PvpStanding, Bracket, WowClass…
│   ├── character   Character, BlizzardCharacterId, CharacterName, Realm,
>│   │               Region, WowClass, Specialization, GearSnapshot, PvpStanding
>   ├── listing     LfgListing, AvailabilitySchedule, TimeWindow, ListingOutcome
│   ├── join        JoinRequest (the group handshake)
│   ├── player      Player, BattleTag, BattleNetId
│   └── synergy     SynergyTable (data, not behaviour)
├── application     use cases and ports
│   ├── port.in     SignInWithBattleNet, GetCurrentPlayer, ImportCharacters,
│   │               PublishListing, SearchBoard, GetCharacter, RequestToJoin,
│   │               AcceptJoinRequest, RejectJoinRequest, WithdrawJoinRequest,
│   │               WithdrawListing
│   ├── port.out    Characters, Listings, BoardListings, ListingLifecycle,
│   │               Players, Seasons, CurrentPlayerProvider, BattleNetCharacters
│   ├── service     the orchestration, one per use case
│   └── error       ResourceNotFoundException, UnauthorizedException
└── adapters
    ├── in.rest     controllers, DTOs, error advice
    ├── in.security the filter chain and the Battle.net success handler
    └── out         persistence · security · season · synergy · blizzard
```

`domain/listing/ListingOutcome` and `domain/join` are the two packages that
exist because of a decision made late; the spec explains why.

---

## The decisions worth arguing about

This is the part an interviewer will ask about. Each entry is one decision,
the reason, and what the alternative would have cost.

### 1. Keyset pagination, never `OFFSET`

The board is read with a cursor: the client sends the `(createdAt, id)` pair
of the last row it received, and PostgreSQL compares it as a **row value**:

```sql
and (l.created_at, l.id) < (:cursorCreatedAt, :cursorListingId)
```

That is one index range on `ix_lfg_listings_board`. `OFFSET` would skip or
repeat rows as soon as a listing lands between two requests — and on a board
that changes every minute, it always does.

The tie-break stays in the database: Java's `UUID.compareTo` is **not**
PostgreSQL's `uuid` order, so re-sorting a page in Java would silently break
the pagination.

The cursor is opaque to the client (versioned base64url): it echoes the
token, it never composes one.

### 2. Anti-enumeration: a missing character and somebody else's are the same 404

`GET /api/characters/{id}` answers `404 "Character not found"` — with the
exact same body — when the id does not exist **and** when it belongs to
another player. The same rule covers a malformed UUID.

The message has exactly **one** source, a factory
(`ResourceNotFoundException.characterNotFound()`) without a message
parameter, so the two paths cannot drift apart. When they do, the caller can
enumerate every character in the game.

### 3. The group handshake, because acceptance cannot be unilateral

Accepting a join request closes **every** active listing of **both**
players, in every bracket, and records a fact for each one — in a single
transaction.

The handshake exists because the alternative was dishonest: accepting
unilaterally would close the owner's listings without their consent, which
lets anyone empty the board on purpose and forge "successes". With the
handshake, `COMPLETED` means *both sides agreed*, and the success rate is
defensible.

Two consequences, both with tests: the owner cannot accept their own
request (422 — it would not form a group and it would forge a success), and
starting to play cancels **every** pending request of the player, not just
of the character that was accepted.

### 4. Bean Validation for presence, and nothing else

Every input field declares its presence: `@NotNull`, `@NotBlank`,
`@NotEmpty`, `@Valid` in cascade, `List<@NotNull @Valid ...>` for list
elements. A missing field is a `422`, never a `500`.

The reason is a threat model, not a preference: **the input is hostile by
default**, and a developer who adds a field to a DTO must not have to
remember to guard it by hand. This was adopted *after* being evaluated and
rejected once, because the hand-written guards had already cost two real
bugs (a missing `schedule` and a `null` inside a `windows` array).

Format still lives in `WireParsing` and business rules still live in the
domain value objects: two mechanisms for two different jobs.

The `422` wording is built from the violation **path**, not from Hibernate
Validator's message bundle — that bundle follows the JVM's locale, so a
Spanish server would answer a Spanish error to an English API.

### 5. The facts outlive their subjects

`listing_outcomes` records what a listing was when it became invisible:
`COMPLETED`, `CANCELLED` or `EXPIRED`. It has **no** foreign key on the
listing and a nullable `character_id` with `ON DELETE SET NULL`.

The reason: the facts are where the metrics come from. A cascade on either
side would delete the history the moment the retention job removed the
listing — or the moment the player left.

The success rate that comes out of them is deliberately **conservative**: it
counts only the groups formed inside the app, so it under-reports. It will be
reviewed with real data.

### 6. The TTL is the server's, and a listing's life is 24 hours

The client never sends a duration. `pvp.listing-ttl: PT24H` is configuration,
and a join request lives exactly as long as the listing it asks for — no
separate TTL, no separate policy.

A listing ends in exactly three ways: it expired, the owner pulled it, or a
group formed. Those are the three kinds of fact.

### 7. Domain objects are born valid

No setters, no `validate()` methods, no half-built objects. `Rating` cannot
be negative; `TimeWindow` cannot end before it starts; `Character` cannot
have specifications of a class it does not play. Validation lives in
constructors and factories, and the failure is a `DomainRuleViolationException`
that the REST edge turns into a `422` with the reason.

### 8. The generic 500 is blank

An unexpected failure answers `500` with the detail `Unexpected error`, no
exception class, no message, no stack trace, no extra properties. The stack
trace goes to the `ERROR` log, where the operator reads it. A caller who
learns *which* failure happened learns something about the server's
internals for free — and the request may be hostile.

### 9. There is no way to invent a character

`POST /api/characters` **does not exist**. A character can only come from the
Battle.net import, and the Blizzard character id is **required** in the
aggregate.

The reason is trust: every rating on the board was **observed**, not claimed.
A board where anybody can insert a character with any rating is a board
nobody can filter by anything real. The cost is honest — a player cannot
register a character for an alt that never logs in, and a character whose
Blizzard account is gone stays on the board until the retention sweep reaches
it.

And the id is *required* rather than optional because it is the only thing
stable against a paid rename or a realm move. It is what makes a re-import
recognise a character instead of duplicating it, and the unique index is on
`(region, blizzard_character_id)` — the region is part of the key because
Blizzard numbers its ids per region.

### 10. The client sends an id and nothing else

The import request carries the `blizzardId` of each character — **no name, no
realm**. The server fetches the account listing with the signed-in player's
token and accepts only ids that are in it; the name and the realm come from
*that* list.

The reason is the upstream. The character endpoint of the Blizzard API is
**public**: it answers for any character in the game, whoever owns it. A
client that could name a character could have asked the server to import
somebody else's — and the only thing that stopped it was the character
already being in our database, which is a **race**, not a guard. The first one
in would have taken it.

The trusted source was already being fetched — the account list. The fix was
to use it as a **gate** instead of only as a catalogue.

And a character of *your* account that our database says belongs to another
player is **rejected**, with a message that says nothing about who has it:
the character was transferred between Battle.net accounts, and we do not
transfer it. That is a conscious limitation — a transferred character is
stuck — because reassigning it would mean trusting our database over the
provider about who owns a character, and the provider is the source of truth
about that.

### 11. One refused character does not abort the import

The player chose five characters. One of them being a level 8 toon with no
specialization is not a reason to lose the other four. The result carries one
outcome per character chosen — `IMPORTED`, `REFRESHED`, `SKIPPED` or
`REJECTED` — and the rejected ones carry the reason.

A batch that died because of one member would throw away work the player
asked for, and would hide the fact that the OTHER four imported fine.

---

## Testing

```bash
./mvnw test     # unit tests only: fast, no Docker
./mvnw verify   # unit tests + integration tests against real PostgreSQL
```

The rules the suite follows:

- **No mocks in the domain.** If a domain test needs one, the design is
  wrong.
- **Hand-rolled fakes at the application boundary.** The application layer
  is tested in pure JUnit: no Spring context, no database.
- **Real PostgreSQL for persistence.** The subject under test is the
  constraint, the index and the cascade. H2 would pass and lie.
- **Integration tests for the whole inbound path.** The web slice wires the
  *real* service on hand-rolled fakes with a frozen clock: JSON → command →
  use case → `201`/`404`/`422`.
- **The third parties are stubbed, never called.** Battle.net and Blizzard
  are answered by stubs built on the JDK's own HTTP server — no new
  dependency. A test run has no business making a network round trip to
  somebody else's server, and a test that did would depend on an account
  that does not belong to the repository.

---

## Where it is going

- [WU17] real players: a `players` table, sign-up with a battle tag, and the
  foreign key that makes `characters.owner_id` a real owner.
- [WU18] Battle.net OAuth2: the session *is* the identity, the handle retired,
  the dev header deleted, and a player exists only because somebody logged in.
- [WU19] the Blizzard import: your real characters, with their observed class,
  specialization, rating and item level — and no way to invent one.
- [WU20] the React client: board, publish, request, accept.
- [WU21] a public deployment (the CV link).
- A lobby with chat — which needs a `Group` entity that does not exist yet.

The working list, with the reasoning behind every decision, lives in
[`docs/domain-spec.md`](docs/domain-spec.md).

---

## Status

Phase 1, no frontend yet. The API is complete for the main flow: sign in with
Battle.net, import your characters from Blizzard, publish availability, browse
the board with filters and pagination, ask to join, accept, reject, withdraw.
The board is public; everything else needs a session. The React client does
not exist.

Every rating on the board was **observed**, not claimed: there is no endpoint
that creates a character by hand, so the numbers other players filter by are
the numbers the game reported. That also means the board is only as fresh as
the last import — a known debt, and the reason the background sync (which
would need a stored refresh token) is written down as out of scope rather
than quietly forgotten.

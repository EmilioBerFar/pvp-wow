# pvp-wow

**LFG board for World of Warcraft arena PvP.** Players register their
characters, publish their availability for a bracket (2v2 / 3v3), and other
players ask to join them. When both sides agree, the search ends and the
listing is recorded as completed.

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

```bash
# Register a character
curl -X POST localhost:8080/api/characters \
  -H 'Content-Type: application/json' -H 'X-Player-Id: <player-uuid>' \
  -d '{"name":"Frostbolt","realm":"Sanguino","region":"EU","wowClass":"MAGE",
       "specs":["MAGE_FROST"],"itemLevel":675,"ratings":{"2v2":1800}}'

# Publish availability
curl -X POST localhost:8080/api/listings \
  -H 'Content-Type: application/json' -H 'X-Player-Id: <player-uuid>' \
  -d '{"characterId":"<uuid>","bracket":"3v3","spec":"MAGE_FROST",
       "languages":["ES"],
       "schedule":{"zone":"Europe/Madrid",
                   "windows":[{"day":"MONDAY","start":"18:00","end":"22:00"}]}}'

# Read the board (newest first, keyset paginated)
curl 'localhost:8080/api/board?region=EU&bracket=3v3&minRating=1800&limit=20'

# Ask to join, and the owner's answer
curl -X POST localhost:8080/api/listings/<listing-id>/join-requests \
  -H 'Content-Type: application/json' -H 'X-Player-Id: <player-uuid>' \
  -d '{"characterId":"<uuid>"}'
curl -X POST localhost:8080/api/join-requests/<id>/accept -H 'X-Player-Id: <owner-uuid>'
```

`X-Player-Id` is a development stub for authentication. It is the **only**
adapter that will be replaced when Battle.net OAuth arrives; the port, the
use cases and the domain never learn the difference.

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
│   ├── listing     LfgListing, AvailabilitySchedule, TimeWindow, ListingOutcome
│   ├── join        JoinRequest (the group handshake)
│   ├── player      Player, BattleTag, Username
│   └── synergy     SynergyTable (data, not behaviour)
├── application     use cases and ports
│   ├── port.in     RegisterCharacter, PublishListing, SearchBoard,
│   │               GetCharacter, RequestToJoin, AcceptJoinRequest,
│   │               RejectJoinRequest, WithdrawJoinRequest, WithdrawListing
│   ├── port.out    Characters, Listings, BoardListings, ListingLifecycle,
│   │               Seasons, CurrentPlayerProvider
│   ├── service     the orchestration, one per use case
│   └── error       ResourceNotFoundException
└── adapters
    ├── in.rest     controllers, DTOs, error advice
    └── out         persistence · security · season · synergy
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

---

## Where it is going

- [WU16] the retention sweep: a daily job that expires overdue listings,
  records their fact and deletes the dead rows after 30 days.
- A lobby with chat — which needs a `Group` entity that does not exist yet.
- The Blizzard API sync, which replaces the declared facts with observed
  ones.

The working list, with the reasoning behind every decision, lives in
[`docs/domain-spec.md`](docs/domain-spec.md).

---

## Status

Phase 1, no frontend yet. The API is complete for the main flow: register a
character, publish availability, browse the board with filters and
pagination, ask to join, accept, reject, withdraw. Authentication is a
development stub; the Blizzard API is not integrated.

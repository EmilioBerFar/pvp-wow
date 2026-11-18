# Domain work spec — Phase 1

This is the work list for the domain layer. You write the code; I review it.
`CharacterName` and `CharacterNameTest` are the reference standard — read them
first, then follow that pattern for everything below. The rules in this doc are
the contract; if something feels wrong, bring it up before fighting it.

## Quick path

1. Read the standard: `domain/character/CharacterName.java` and its test.
2. Build WU1 → WU6 in order. After each unit, `./mvnw test` must be green.
3. Ping me for review after each work unit — small reviews beat big ones.

## Ground rules

| Rule | Why |
|------|-----|
| Zero framework imports under `domain/` | Hexagonal dependency rule. The domain compiles and tests on its own |
| Objects are born valid | Validation lives in constructors/factories. No setters, no `validate()` methods, no half-built objects |
| Value objects = records | Value equality and immutability for free |
| Aggregates = classes, equality by id only | Two `Character`s with the same id are the same character even if a field differs |
| Immutable by default | `Set.copyOf` / `Map.copyOf` inside constructors — never expose mutable internals |
| Cross-aggregate rules do NOT go in aggregates | e.g. "listing spec must be one of the character's specs" — the aggregate cannot see other aggregates; that is a use case job |
| `null` = programming error → NPE | Domain rule broken → `DomainRuleViolationException` (see `CharacterName`) |

Purity check (must print nothing):

```powershell
Get-ChildItem -Recurse src\main\java\com\pvpwow\lfg\domain -Filter *.java |
  Select-String -Pattern 'import\s+(org\.|jakarta\.|com\.fasterxml)'
```

## WU1 — Identity and character value objects

| Class | Package | Kind | Rules |
|-------|---------|------|-------|
| `CharacterId` | `domain.character` | `record(UUID value)` | Non-null |
| `PlayerId` | `domain.player` | `record(UUID value)` | Non-null (needed by `Character`) |
| `Realm` | `domain.character` | `record(String value)` | Trim; collapse inner whitespace runs to one space; lowercase. `"Sanguino"` equals `"sanguino"`. Display casing is a render concern, not a domain concern. **Open debt:** free text in the bootstrap phase. The same EU realm is named differently per client locale ("Sanguino" / "Bloodhoof"), so two players on one realm can fail to match and `name + realm` uniqueness cannot be trusted. Fix when the API arrives: a realm catalog seeded from Blizzard's realm index, and `Realm` keyed by the canonical slug |
| `Region` | `domain.character` | enum | `EU`, `US`, `KR`, `TW`, `CN` — rated PvP is cross-realm within a region only |
| `WowClass` | `domain.character` | enum | 13 constants (see below) |
| `Specialization` | `domain.character` | enum | 39 constants, each carrying its `WowClass` AND its `Role`; static `forClass(WowClass)` returns the `Set` of its specs (see below) |
| `Role` | `domain.character` | enum | `TANK`, `HEALER`, `MELEE_DPS`, `RANGED_DPS` — the melee/ranged split is the first question when building an arena comp; the classic 3-way split is too coarse. Roles belong to specs, never to classes |
| `Rating` | `domain.character` | `record(int value)` | `>= 0` — impossibility only. The old `0..3500` cap was a plausibility guess pretending to be an invariant: cutoffs move, and the observed truth will come from the Blizzard API. Named `Rating` (visible CR), not `Mmr`: what players report by hand is their CR; MMR is Blizzard's hidden matchmaking value |
| `ItemLevel` | `domain.character` | `record(int value)` | `> 0` — impossibility only. Plausibility ("is 900 believable this season?") is modeled NOWHERE, not even as a policy: caps move every season, and the observed truth will come from the Blizzard API. Required on every character: gear determines arena readiness |
| `Bracket` | `domain.character` | enum | `TWO_VS_TWO` (teamSize 2), `THREE_VS_THREE` (teamSize 3). Character package, not listing: ratings-per-bracket need it here and package dependencies flow listing → character, never the reverse |

`WowClass` constants: `DEATH_KNIGHT, DEMON_HUNTER, DRUID, EVOKER, HUNTER, MAGE,
MONK, PALADIN, PRIEST, ROGUE, SHAMAN, WARLOCK, WARRIOR`.

`Specialization` constants (class-prefixed because names like FROST or HOLY
repeat across classes):

| Class | Constants |
|-------|-----------|
| DEATH_KNIGHT | `DEATH_KNIGHT_BLOOD`, `DEATH_KNIGHT_FROST`, `DEATH_KNIGHT_UNHOLY` |
| DEMON_HUNTER | `DEMON_HUNTER_HAVOC`, `DEMON_HUNTER_VENGEANCE` |
| DRUID | `DRUID_BALANCE`, `DRUID_FERAL`, `DRUID_GUARDIAN`, `DRUID_RESTORATION` |
| EVOKER | `EVOKER_DEVASTATION`, `EVOKER_PRESERVATION`, `EVOKER_AUGMENTATION` |
| HUNTER | `HUNTER_BEAST_MASTERY`, `HUNTER_MARKSMANSHIP`, `HUNTER_SURVIVAL` |
| MAGE | `MAGE_ARCANE`, `MAGE_FIRE`, `MAGE_FROST` |
| MONK | `MONK_BREWMASTER`, `MONK_MISTWEAVER`, `MONK_WINDWALKER` |
| PALADIN | `PALADIN_HOLY`, `PALADIN_PROTECTION`, `PALADIN_RETRIBUTION` |
| PRIEST | `PRIEST_DISCIPLINE`, `PRIEST_HOLY`, `PRIEST_SHADOW` |
| ROGUE | `ROGUE_ASSASSINATION`, `ROGUE_OUTLAW`, `ROGUE_SUBTLETY` |
| SHAMAN | `SHAMAN_ELEMENTAL`, `SHAMAN_ENHANCEMENT`, `SHAMAN_RESTORATION` |
| WARLOCK | `WARLOCK_AFFLICTION`, `WARLOCK_DEMONOLOGY`, `WARLOCK_DESTRUCTION` |
| WARRIOR | `WARRIOR_ARMS`, `WARRIOR_FURY`, `WARRIOR_PROTECTION` |

Role assignment (the second field of every `Specialization` constant):

| Class | TANK | HEALER | MELEE_DPS | RANGED_DPS |
|-------|------|--------|-----------|------------|
| DEATH_KNIGHT | `DEATH_KNIGHT_BLOOD` | — | `DEATH_KNIGHT_FROST`, `DEATH_KNIGHT_UNHOLY` | — |
| DEMON_HUNTER | `DEMON_HUNTER_VENGEANCE` | — | `DEMON_HUNTER_HAVOC` | — |
| DRUID | `DRUID_GUARDIAN` | `DRUID_RESTORATION` | `DRUID_FERAL` | `DRUID_BALANCE` |
| EVOKER | — | `EVOKER_PRESERVATION` | — | `EVOKER_DEVASTATION`, `EVOKER_AUGMENTATION` |
| HUNTER | — | — | `HUNTER_SURVIVAL` | `HUNTER_BEAST_MASTERY`, `HUNTER_MARKSMANSHIP` |
| MAGE | — | — | — | `MAGE_ARCANE`, `MAGE_FIRE`, `MAGE_FROST` |
| MONK | `MONK_BREWMASTER` | `MONK_MISTWEAVER` | `MONK_WINDWALKER` | — |
| PALADIN | `PALADIN_PROTECTION` | `PALADIN_HOLY` | `PALADIN_RETRIBUTION` | — |
| PRIEST | — | `PRIEST_DISCIPLINE`, `PRIEST_HOLY` | — | `PRIEST_SHADOW` |
| ROGUE | — | — | `ROGUE_ASSASSINATION`, `ROGUE_OUTLAW`, `ROGUE_SUBTLETY` | — |
| SHAMAN | — | `SHAMAN_RESTORATION` | `SHAMAN_ENHANCEMENT` | `SHAMAN_ELEMENTAL` |
| WARLOCK | — | — | — | `WARLOCK_AFFLICTION`, `WARLOCK_DEMONOLOGY`, `WARLOCK_DESTRUCTION` |
| WARRIOR | `WARRIOR_PROTECTION` | — | `WARRIOR_ARMS`, `WARRIOR_FURY` | — |

## WU2 — Character aggregate

Class (not a record) in `domain.character`. State: `CharacterId id`,
`PlayerId ownerId`, `CharacterName name`, `Realm realm`, `Region region`,
`WowClass wowClass`, `GearSnapshot gear`, `Set<Specialization> specs`,
`Map<Bracket, PvpStanding> standings`.

**Declared vs observed** — the seam that makes the Blizzard API step cheap:
`specs` (which specializations the player is WILLING to play), languages,
availability and the battle tag are declared and stay manual forever — no API
can observe a preference. Name, realm, class, gear and rating are observed from
an external source, so they carry their own evidence:

- `GearSnapshot(ItemLevel itemLevel, Instant observedAt)` — an item level
  without a date cannot exist; two separate fields would drift.
- `PvpStanding(Rating rating, PvpSeason season, Instant observedAt)` — a rating
  without a season is meaningless (ratings reset every season) and Blizzard's
  own API returns `season.id` alongside `rating`. Blizzard's profile docs
  require clients to store the request timestamp and revalidate every 30 days:
  freshness is not solved by the API, it is made mandatory.

- Two doors, never more: `register(ownerId, name, realm, region, wowClass,
  gear, specs, standings)` creates — identity generated inside;
  `rehydrate(id, ...)` reconstitutes from persisted state — identity received
  because it already exists. No setters either way.
- Factory rules: `specs` non-empty AND every spec belongs to `wowClass`
  (hint: `Specialization.forClass(wowClass).containsAll(specs)`); defensive
  immutable copies of `specs` and `standings`.
- `standings` may be empty or partial — a rating per bracket is optional; an
  unranked bracket simply has no entry (`standingFor` returns empty). The search
  use case (later step) must define how unranked listings sort, how stale a
  standing may be before it is shown as unknown, and how item-level proximity
  filters — gear gaps matter in rated PvP.
- `equals`/`hashCode` by `id` only.
- Queries: `standingFor(Bracket)` → `Optional<PvpStanding>`, `ratingFor(Bracket)`
  → `Optional<Rating>` (convenience, delegates), `gear()` → `GearSnapshot`,
  `itemLevel()` → `ItemLevel` (convenience), `plays(Specialization)` →
  `boolean`, `roles()` → derived `Set<Role>` from `specs` — computed on demand,
  never stored.

**No `armoryUrl()` in the domain.** It moved to the edge
(`adapters.in.rest.ArmoryLinks`) for three verified reasons: the page locale is
presentation (deriving eu→es-es served Spanish pages to every EU player), the
realm slug must be the CANONICAL one from the profile API (the same EU realm is
"Sanguino" or "Bloodhoof" depending on the client locale, so user-entered text
cannot produce it), and path segments need percent-encoding (`Ñandú` produced a
broken URL). Still derived, still never stored — just derived where the inputs
are trustworthy.

**URL shape — copied from a live character (2026-11-01), not inferred:**
`https://worldofwarcraft.blizzard.com/{locale}/{game-version}/{region}/armory/character/{realm-slug}/{name}`,
e.g. `.../es-es/worldsoul/eu/armory/character/dun-modr/strongem`. The
game-version segment is per-expansion (`worldsoul` for The War Within,
`classic` for Classic), so it is an adapter constant pinned by a test against
the real URL — never a domain concept. Name and slug are lowercase in live
URLs even though in-game names are capitalized. The first version of this
class built `/{locale}/character/{slug}/{name}` and would have linked nowhere.

## WU3 — Player

| Class | Kind | Rules |
|-------|------|-------|
| `Username` | `domain.player`, `record(String value)` | Trim; 3–24 chars; only letters, digits, `.`, `_`, `-`; must start with a letter |
| `BattleTag` | `domain.player`, `record(String value)` | Battle.net contact identity — account-level, not per character. Trim; lowercase the name part. Format: 3–12 alphanumerics, `#`, 4–5 digits (e.g. `emilio#12345`); digits in the name part are allowed — verified against a real tag |
| `Player` | `domain.player`, **class** | Aggregate root of the account. State: `PlayerId id`, `Username username`, `BattleTag battleTag`. Same pattern as `Character`: two doors (`register` generates the id, `rehydrate` receives it), no setters, `equals`/`hashCode` **by id only** — a record here would give structural equality, and a player who changes battle tag would stop being the same player. The battle tag is required: it is the ONLY contact channel in the product. Persisted since WU17; Battle.net OAuth later enriches this concept, never replaces it |

## WU4 — Listing value objects

| Class | Kind | Rules |
|-------|------|-------|
| `Language` | `domain.listing`, enum | `ES, EN, PT, FR, DE, IT` each carrying its ISO 639-1 code. EU-centric set; extendable |
| `TimeWindow` | `domain.listing`, `record(DayOfWeek day, LocalTime start, LocalTime end)` | `start` strictly before `end`; no midnight wrap in v1; half-open `[start, end)` |
| `AvailabilitySchedule` | `domain.listing`, `record(Set<TimeWindow> windows, ZoneId zone)` | At least one window; no duplicate windows; immutable copy. Without the zone, "evenings" means nothing between Madrid and Mexico City |
| `ListingStatus` | `domain.listing`, enum | `ACTIVE`, `CANCELLED`, `EXPIRED` |
| `ListingId` | `domain.listing`, `record(UUID value)` | Non-null |

## WU5 — LfgListing aggregate

Class in `domain.listing`. State: `ListingId id`, `CharacterId characterId`,
`Bracket bracket`, `Specialization spec` (snapshot pinned at publish time),
`Set<Language> languages`, `AvailabilitySchedule schedule`, `ListingStatus
status`, `Instant createdAt`, `Instant expiresAt`.

- Factory `publish(characterId, bracket, spec, languages, schedule, now, ttl)` →
  status `ACTIVE`, `createdAt = now`, `expiresAt = now + ttl`. Rules: at least
  one language; positive `ttl`.
- `cancel()`: only from `ACTIVE`, else `DomainRuleViolationException`.
- `expireIfDue(Instant now)` → `boolean`: `ACTIVE` → `EXPIRED` when
  `now >= expiresAt`; idempotent; returns whether it transitioned.
- `isActive()` query. `equals`/`hashCode` by `id` only.
- Reminder: "spec must be one of the character's specs" is NOT validated here —
  cross-aggregate, use case territory.
- The listing's role is derived (`spec().role()`), never a stored field —
  stored derivations drift.

## WU6 — Synergy

| Class | Kind | Rules |
|-------|------|-------|
| `SynergyScore` | `domain.synergy`, `record(int value)` | `0..100` inclusive |
| `SynergyTable` | `domain.synergy`, `record(Map<WowClass, Map<WowClass, SynergyScore>> scores)` | Static `from(Map)` validates a complete symmetric 13×13 matrix (same-class pairs count: Mage+Mirror is a real comp); `score(WowClass, WowClass)` symmetric lookup; deep immutable copies |

The JSON file and the catalog port are NOT yours — that is the adapter step.

## WU7 — RegisterCharacter (application layer, first use case)

| Piece | Package | Content |
|---|---|---|
| Driving port | `application.port.in` | `RegisterCharacter` interface, nested `RegisterCharacterCommand` |
| Driven ports | `application.port.out` | `Characters` (save + `alreadyRegistered`), `CurrentPlayerProvider` (X-Player-Id stub phase), `Seasons` (`current()`) |
| Service | `application.service` | `RegisterCharacterService` — orchestrates through the out ports |

- **Command shape — the declared/observed seam applied to the contract**
  (decided 2026-11-01, option C): declared facts arrive as value objects
  (name, realm, region, class, the specs the player is willing to play) —
  pure data, valid by construction, nothing left to add. Observed facts
  arrive RAW (`int itemLevel`, `Map<Bracket, Integer> ratings`) on purpose:
  the use case is the witness, so it owns the stamps. The policy "a
  self-report is observed at registration" lives where registration happens;
  every future adapter (CLI, Blizzard sync) inherits it instead of
  re-deciding it.
- Ratings are OPTIONAL at registration (product decision from day one); a
  missing bracket simply stays unranked. A rating needs its season, hence the
  `Seasons` port — the active season is an operational fact (config in
  bootstrap, the Blizzard sync later).
- `ownerId` never travels in the command: the use case asks
  `CurrentPlayerProvider` who is calling.
- **Uniqueness (region, realm, name)**: friendly pre-check through the port
  (`alreadyRegistered` → `DomainRuleViolationException` "already
  registered"). Advisory by nature — two simultaneous registrations can race
  past it until the persistence step adds the unique index; the index is the
  law, the pre-check is UX.
- `RegisterCharacterService` stamps with an injected `java.time.Clock`;
  tests use `Clock.fixed`, so the stamps are assertable to the exact instant.
- Behavior tests use HAND-ROLLED fakes of the out ports — that is what ports
  are for. Domain tests stay mockless; application tests fake the edges. No
  Spring context, no database: the application layer is testable in pure JUnit.
- Wiring (REST controller + advice, config-backed `Seasons`, dev
  `CurrentPlayerProvider` reading `X-Player-Id`, JPA `Characters`) is the
  adapters step, not this one.

## WU8 — The inbound edge (REST)

| Piece | Package | Content |
|---|---|---|
| Controller | `adapters.in.rest` | `POST /api/characters` → 201 + `Location` + `{id}` |
| Request DTO | `adapters.in.rest` | `RegisterCharacterRequest.toCommand()` — the adapter cost of option C: declared strings become VOs HERE, invalid ones die HERE (422), the use case never sees them. Bracket keys are friendly (`2v2`, `3v3`); everything else uses enum names, case-insensitive |
| Advice | `adapters.in.rest` | `ApiErrorHandling` — the error strategy as code: DomainRuleViolation → 422 ProblemDetail + DEBUG log; Unauthorized → 401; malformed body → 400; unexpected → 500 + generic detail + the first ERROR log of the codebase. Spring's own framework errors pass through with their own status |
| Dev auth | `adapters.out.security` | `DevCurrentPlayerProvider` reads `X-Player-Id` via the request context; missing/garbage/no-context → `UnauthorizedException`. Since WU18 it is `@Profile("dev")` only |
| Real auth | `adapters.in.security` + `adapters.out.security` | The OAuth2 filter chain, `BattleNetSignInHandler` (claims → player → session) and `SessionCurrentPlayerProvider` (session → `PlayerId`); active in every profile except `dev` |
| Season config | `adapters.out.season` | `ConfigSeasons` — `pvp.current-season` property, bootstrap-only |
| Bootstrap persistence | `adapters.out.persistence` | `InMemoryCharacters` — stand-in so the app runs end to end BEFORE the database exists; **replaced by `JpaCharacters` in WU9** |
| Wiring | `adapters` | `WiringConfig` — the composition root: the ONLY place a framework-free application service becomes a bean. It speaks PORTS, never adapter classes |

- The web slice is tested with `@WebMvcTest` plus a nested `@Configuration`
  that hand-wires the REAL service on hand-rolled fakes and `Clock.fixed` —
  no Mockito, no database, no full context. The whole inbound path is
  proven: JSON → command → use case → 201/422.
- Boot 4 note: `@WebMvcTest` lives in
  `org.springframework.boot.webmvc.test.autoconfigure` (the split moved it).
- Running the whole app today needs `docker compose up -d` first, because
  JPA autoconfiguration still connects at startup even with zero entities —
  that connection is not used by the in-memory adapter, but Boot insists.
  The smoke run of the booted app is still pending: Docker Desktop was down
  when WU8 landed.

## WU9 — The persistence edge (JPA + Flyway)

| Piece | Package | Content |
|---|---|---|
| Migration | `db/migration` | `V1__create_characters.sql` — `characters` root, `character_specs`, `character_standings`, the unique index on `(region, realm, lower(name))`, and an index on `(bracket, rating desc)` for the board |
| Entity | `adapters.out.persistence` | `CharacterEntity` — relational shape, mutable, package-private. Domain objects never leak here as-is |
| Embeddable | `adapters.out.persistence` | `StandingEmbeddable` — rating + season + observedAt; the bracket is the map key |
| Mapper | `adapters.out.persistence` | `CharacterEntityMapper` — both directions total; `toDomain` always uses `rehydrate` (repositories never invent ids) |
| Adapter | `adapters.out.persistence` | `JpaCharacters` — `EntityManager` + `TransactionTemplate`, no `@Transactional` (the class is `final`) |
| Repository | `adapters.out.persistence` | `CharacterJpaRepository` — only the pre-check query; the save path is hand-written |

- **Relational children, not `jsonb` (user decision).** `SearchBoard` is the
  next unit and it filters by spec and sorts by rating inside a bracket; that
  is a join and an index, not JSON gymnastics. The cost is three tables.
- **`save` is explicit on purpose.** The domain assigns the id, so Spring
  Data's "is it new?" heuristic answers NO and routes every call to `merge`,
  which on a missing row issued only the SELECT and never inserted. The
  adapter asks the database and chooses `persist` or `merge`. Identity
  belongs to us, not to a framework heuristic.
- **The unique index is the law, and it is casing-blind.** `CharacterName`
  keeps display case (`Frostbolt`), so the index is functional on
  `lower(name)`; otherwise registering the same toon twice by recasing it
  would slip through. `alreadyRegistered` mirrors the index expression.
- **`owner_id` has no FK yet, deliberately.** There is no `players` table and
  no `RegisterPlayer` use case, and the dev stub accepts any UUID; a FK today
  would reject every insert. It arrives with the players table.
- **Tests against real Postgres.** `JpaCharactersIT` uses Testcontainers
  (`postgres:18-alpine`) and runs in failsafe (`mvnw verify`), so `mvnw test`
  stays fast and Docker-free. H2 in PostgreSQL mode would pass and lie: the
  subject under test is exactly the constraint H2 imitates.
- **Boot 4 traps paid for here:** (1) `flyway-core` alone does NOT
  auto-configure — the starter is `spring-boot-starter-flyway`, otherwise
  `ddl-auto: validate` reports every table as missing; (2) Testcontainers
  2.x renamed artifacts AND packages: `org.testcontainers:testcontainers-postgresql`,
  class `org.testcontainers.postgresql.PostgreSQLContainer`, and it is no
  longer generic.
- **`postgres:18` changed the volume layout:** mounting at
  `/var/lib/postgresql/data` makes the container exit; 18+ wants a single
  mount at `/var/lib/postgresql`.
- `ddl-auto: validate` stays: Flyway owns the schema, Hibernate only checks
  that the mapping still matches it. Datasource credentials come from
  `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, defaulting to the compose values.

## WU10 — Publish and search the board (application layer)

| Piece | Package | Content |
|---|---|---|
| Publish driving port | `application.port.in` | `PublishListing` + command: character, bracket, pinned spec, languages and schedule. No client TTL |
| Publish service | `application.service` | `PublishListingService` — resolves the caller, loads the character, enforces ownership/spec membership/active uniqueness, stamps time and saves |
| Listing write port | `application.port.out` | `Listings` — save plus the advisory active-duplicate query. `Characters.findById` added for the cross-aggregate checks |
| Search driving port | `application.port.in` | `SearchBoard` — mandatory region/bracket; optional class, minimum CR and minimum item level; returns domain-backed `BoardEntry` rows |
| Board read port | `application.port.out` | `BoardListings` — the query contract PostgreSQL must execute; separate from the listing write port |
| Search service | `application.service` | `SearchBoardService` — supplies now/current season, delegates filtering and recency order, marks stale ratings without mutating them |

- **One ACTIVE listing per character and bracket (user decision).** 2v2 and
  3v3 are independent. Publishing a duplicate is a domain violation (422 at
  the REST edge); expiry or cancellation frees the slot. The future database
  constraint, not the pre-check, must win concurrent races.
- **Missing and foreign characters are indistinguishable (user decision,
  OWASP anti-enumeration).** An unknown character id and a character owned by
  another player both return 404 with the same body — "Character not found" —
  and no name, realm or region. The caller must not learn whether a character
  exists. The spec the character did not declare and the active duplicate stay
  422: they are invalid business requests, not missing resources.
- **Starting to play closes all the user's listings (user decision).** Closing
  one of your own listings to play and accepting another player's invitation
  both trigger the same bulk close for that user. This belongs to future
  `CompleteGroup`/`AcceptInvitation` use cases, not to `PublishListing` or an
  isolated `cancel()`. Listings are closed, not deleted.
- **Server-owned 24-hour TTL (user decision).** The service receives a
  `Duration`; adapter configuration will supply `PT24H`. No inbound command
  can extend its own visibility.
- **Recency, not synergy, controls visibility (user decision).** Results are
  ordered `createdAt DESC`, so meta classes do not permanently occupy the top.
  Optional filters: `WowClass`, minimum visible CR (`Rating`, never hidden
  MMR), and minimum item level.
- **Old evidence stays visible but cannot satisfy a CR filter (user
  decision).** A rating from another season or observed more than 30 days ago
  is marked `ratingStale`; its value and `observedAt` remain visible. With no
  minimum-CR filter the candidate stays on the board. At exactly 30 days the
  observation is still fresh. Unranked is not stale.
- **Gear follows the same freshness policy (user decision).** A gear snapshot
  observed more than 30 days ago is marked `gearStale`; its item level and
  `observedAt` remain visible, but a minimum-item-level filter only matches
  gear observed at or after the same 30-day threshold. At exactly 30 days the
  snapshot is still fresh.
- **Read policy is split from query execution.** `SearchBoardService` owns
  time, season and freshness; `BoardListings` owns active/unexpired filtering,
  optional predicates and recency ordering. The future SQL adapter must prove
  those behaviors against real Postgres. Loading everything and filtering in
  Java would waste the WU9 relational design.
- WU10 is application-layer only: no listing tables, REST endpoints or Spring
  wiring yet. Pagination must be bounded before the board is exposed publicly.

## WU11 — Listing persistence and the real board query

| Piece | Package | Content |
|---|---|---|
| Migration | `db/migration` | `V2__create_listings.sql` — `lfg_listings` root, `listing_languages`, `listing_windows`, partial unique index on `(character_id, bracket)` for ACTIVE rows, and the board index on `(bracket, created_at desc, id desc)` |
| Entities | `adapters.out.persistence` | `ListingEntity` — listing root with a LAZY character reference; `WindowEmbeddable` — one persisted availability window |
| Write mapper | `adapters.out.persistence` | `ListingEntityMapper` — total translation; persists ISO language codes and day names, never enum constant names |
| Write adapter | `adapters.out.persistence` | `JpaListings` — one transaction: expire the character's due ACTIVE rows, then `persist`; a unique-index rejection becomes the business duplicate rule |
| Repository | `adapters.out.persistence` | `ListingJpaRepository` — the expiry sweep and the advisory active query |
| Read adapter | `adapters.out.persistence` | `JdbcBoardListings` — explicit SQL, optional predicates, the freshness `EXISTS`, `createdAt DESC, id DESC`, and batched hydration |

- **Expire, then insert (user decision).** PostgreSQL cannot use `now()` in a
  partial index predicate, so an overdue-but-ACTIVE row would otherwise block
  the slot forever. `JpaListings.publish` marks the character's due ACTIVE rows
  EXPIRED using the new listing's `createdAt` as the reference, then inserts in
  the same transaction.
- **Publish accepts only a live aggregate.** `JpaListings.publish` rejects a
  listing that is not live at its own `createdAt`, so a cancelled or expired
  aggregate can never resurrect as a row. `LfgListing.isActiveAt(now)` is the
  same predicate as the board SQL — `status = 'ACTIVE' and expires_at > now` —
  and at exactly `expiresAt` the listing is already due.
- **The partial unique index is still the law.** `ux_lfg_listings_active`
  arbitrates concurrent publishers that both raced past the application-level
  pre-check. The adapter reads SQLState 23505 from the cause chain and turns
  the rejection into `DomainRuleViolationException` only when an active row
  actually exists; driver classes stay out of the adapter because the driver is
  runtime scope.
- **Reads use JdbcTemplate; writes use JPA (user decision).** The board needs
  optional predicates, a current-season freshness `EXISTS` and deterministic
  ordering; a JPA fetch graph over four collections would multiply rows and
  still need deduplication. Writes stay on JPA, where `persist` and the
  transaction are explicit.
- **The rating filter is an `EXISTS`, not a join.** A minimum CR matches a
  standing for the same bracket, current season, at or after
  `ratingFreshAfter`, with `rating >= minimumRating`. Stale, previous-season
  and unranked evidence cannot satisfy it, but stays visible when no minimum
  is requested.
- **Aggregate hydration is batched.** The listing/character root comes from the
  filtered query; languages, windows, specs and standings are loaded by id
  lists. Each query has one job and no cartesian product.
- **Class and item-level filters are direct predicates** on
  `characters.wow_class` and `characters.item_level`; when a minimum item level
  is requested the query also requires `characters.gear_observed_at >=
  evidenceFreshAfter`, so old gear cannot satisfy it. The region and bracket
  predicates are mandatory, as in the port.
- **Staleness stays application-owned.** `SearchBoardService` computes
  `evidenceFreshAfter` and marks both `ratingStale` and `gearStale`; the
  adapter only executes criteria. The board never mutates observed evidence.
- **Boundaries.** WU11 adds no REST endpoints, no bean wiring of the use cases
  and no TTL configuration; those are WU12. Pagination is still absent from the
  port and must be bounded before the board is public.
- **Tests against real Postgres.** `JpaListingsIT` covers the round trip, the
  expiry sweep, the active query boundary, the publish guard and the duplicate
  index. `JdbcBoardListingsIT` covers region/bracket isolation, overdue and
  cancelled rows, class/item-level/rating filters, the exactly-30-day
  boundaries for ratings and gear, previous-season and unranked evidence,
  recency order and full aggregate hydration. Both run in failsafe
  (`mvnw verify`).
- **Retention is a scheduled job, not lazy cleanup (user decision, WU16).**
  A daily job marks overdue ACTIVE listings EXPIRED and deletes the rows that
  are no longer ACTIVE, older than 30 days. Publishing already expires the
  character's due rows for the bracket it touches, but every other overdue row
  stays ACTIVE until then, so the sweep is required before the board is public.

## WU12 — Expose the board and the publication flow (REST + wiring + paging)

| Piece | Package | Content |
|---|---|---|
| Board read port | `application.port.out` | `BoardListings` now takes a `BoardCursor` and a `limit`, and returns a `BoardSlice` (`candidates`, `hasMore`) instead of an unbounded list |
| Search driving port | `application.port.in` | `SearchBoardQuery` gains `cursor` and `limit`; `BoardPage(entries, nextCursor)` is the new result. `DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 50` |
| Search service | `application.service` | `SearchBoardService` translates the cursor between the two port vocabularies and anchors the next one on the last returned entry |
| Read adapter | `adapters.out.persistence` | `JdbcBoardListings` adds the keyset predicate and `limit + 1` detection |
| Publish endpoint | `adapters.in.rest` | `POST /api/listings` → `201` + `{id}` |
| Board endpoint | `adapters.in.rest` | `GET /api/board` → `200` + `{items, nextCursor}` |
| Request DTO | `adapters.in.rest` | `PublishListingRequest` with nested schedule/window records; no TTL field exists. Presence is declared with Bean Validation |
| Response DTO | `adapters.in.rest` | `BoardPageResponse` — a deliberate projection of `BoardPage` |
| Cursor codec | `adapters.in.rest` | `BoardCursorCodec` — versioned, opaque, base64url |
| Wire conventions | `adapters.in.rest` | `WireParsing` — one home for `required`, enum parsing, friendly brackets, ISO times and zone ids |
| Wiring | `adapters` | `WiringConfig` exposes `PublishListing` and `SearchBoard`; `pvp.listing-ttl` is validated fail-fast |

- **Keyset pagination, never offset (user decision).** The cursor is the
  `(createdAt, id)` pair of the last row of the previous page — the same
  composite order the board already used. SQL compares it as a row value,
  `(l.created_at, l.id) < (:cursorCreatedAt, :cursorListingId)`, which
  PostgreSQL turns into one index range on `ix_lfg_listings_board`. Two OR'd
  conditions would not use the index the same way. The query fetches
  `limit + 1` rows: the extra row answers "is there another page?" without a
  count query, and it is never hydrated or returned.
- **The tie-break is the database's, not Java's.** `order by l.created_at desc,
  l.id desc` and the cursor comparison both happen in PostgreSQL, so UUID
  ordering is consistent. Nothing re-sorts the page in Java — Java's
  `UUID.compareTo` is NOT PostgreSQL's uuid order.
- **Presence is DECLARED, not remembered (user decision).** Bean Validation
  owns presence and nothing else: `@NotNull`, `@NotBlank`, `@NotEmpty`,
  `@Valid` in cascade, and `List<@NotNull @Valid ...>` for list elements. The
  rationale is a threat model, not a preference: the input is hostile by
  default, and a developer who adds a field to a DTO must not have to
  remember to guard it by hand. A missing field is a 422, never a 500.
- **Format and business rules did NOT move.** `WireParsing` still parses
  (friendly brackets, case-insensitive enums, ISO times, zone ids) and the
  domain value objects still validate what they are. Two mechanisms for two
  different jobs, never three for one. The annotation vocabulary says
  "this must be here"; the value object says "this must be correct".
- **The 422 wording is built from the violation PATH, not from the message
  bundle.** Hibernate Validator's default messages come from a resource
  bundle, and that bundle follows the JVM's locale: a Spanish server would
  answer a Spanish error to an English API. The advice builds
  `<path> is required` itself, so the answer is the same everywhere. The path
  keeps the nested structure: `schedule.windows[0].day is required`.
- **The one optional field is deliberate.** `ratings` may be absent (a
  character may be registered unranked); its CONTENTS are constrained when
  present. Every other field of both DTOs is required.
- **The declaration is a test.** `everyFieldOfTheRequestIsDeclaredRequired`
  walks the record's fields and their presence constraints (including the type
  arguments of collections), so a field added without an annotation fails the
  suite instead of production. `aMissingRequiredFieldIsA422` keeps one
  parameterized case per required field: a JSON null binds exactly like an
  absent field, so each case proves both.
- **A missing required field is a 422, never an NPE — for EVERY field.** This
  covers nested objects (`schedule`, `zone`, `windows`) and null ENTRIES inside
  an array (`windows: [null]`, `specs: [null]`), not just top-level fields.
- **The cursor is opaque (user decision).** Versioned `1|instant|uuid`,
  base64url without padding, encoded and decoded only in the REST adapter. The
  client echoes it, it never composes one, so the cursor shape can change
  without breaking callers. The application layer never sees base64.
- **Each port owns its own cursor vocabulary.** `SearchBoard.BoardCursor` and
  `BoardListings.BoardCursor` are two records with the same shape; the service
  translates. Neither port imports the other just to save a mapping.
- **Error semantics are split by audience.** Missing `region`/`bracket` is
  Spring's own 400 (a binding failure). A cursor this API did not issue is a
  `MalformedCursorException` → 400: it is a wire artifact, not a business rule.
  Unknown enums, a negative rating and an out-of-range limit stay 422, because
  those are invalid values the caller can correct. The `limit` bound is also
  enforced inside `SearchBoardQuery`, so a future adapter that forgets to
  validate gets a loud failure instead of an unbounded query.
- **201 carries the id and NO `Location`.** There is no read-by-id resource, so
  a Location header would point at a 404. **Pre-existing debt, not fixed here:**
  `POST /api/characters` (WU8) already returns `Location: /api/characters/{id}`
  with no such endpoint. It is removed when that read resource exists, or the
  header is dropped there too.
- **The board response is a projection, not an aggregate dump.** It carries the
  pinned spec, languages, sorted availability windows, `createdAt`/`expiresAt`,
  and the character summary with gear and rating evidence for THE QUERIED
  bracket only. `ownerId` is deliberately absent: the board is public and the
  owner's identity is not needed to answer "who is available". Stale evidence
  is still sent, flagged `ratingStale`/`gearStale`; unranked means a null
  rating and no staleness. Collections are sorted so the same board always
  serializes identically.
- **TTL is configuration, not input.** `pvp.listing-ttl: PT24H` feeds
  `PublishListingService` through the composition root. Zero or negative fails
  the boot — a misconfiguration must not become listings that are born due.
- **Exposing the board does NOT require the retention sweep for correctness.**
  The query filters `status='ACTIVE' and expires_at > now`, so overdue rows
  never appear. The daily job remains necessary hygiene (storage and the
  publish pre-check), and it stays scheduled for a later WU.
- **Boundaries.** No read-by-id, no cancel/close endpoint (that belongs to the
  future `CompleteGroup`/`AcceptInvitation` flow), no retention job, no real
  auth beyond the `X-Player-Id` stub.
- **Tests.** `JdbcBoardListingsIT` walks five rows sharing the same
  `created_at` in pages of two and proves the walk matches one big query with
  no gaps or duplicates; it also covers the full-page-last-page and
  cursor-at-the-oldest-row cases. `SearchBoardServiceTest` covers the cursor
  translation and anchoring. `PublishListingControllerTest` and
  `SearchBoardControllerTest` run the REAL services on hand-rolled fakes:
  JSON → command → 201/404/422/400, the stale flags on the wire, and the
  cursor round trip.

## WU14 — Hostile input by default (Bean Validation + a generic 500)

| Piece | Package | Content |
|---|---|---|
| DTO constraints | `adapters.in.rest` | `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Valid` in cascade and `List<@NotNull @Valid ...>` on both request records |
| Controller binding | `adapters.in.rest` | `@Valid @RequestBody` on both POST endpoints |
| Advice | `adapters.in.rest` | `MethodArgumentNotValidException` → 422; `ConstraintViolationException` → 422 |
| Violation wording | `adapters.in.rest` | `WirePresence` — builds `<path> is required` from the violation path, locale-independent |
| Generic 500 | `adapters.in.rest` | the anomaly handler answers a blank ProblemDetail: generic detail, no extra properties |

- **Presence is declared, not remembered (user decision).** The input is
  hostile by default. A developer who adds a field to a DTO must not have to
  remember to guard it by hand — the declaration says "this must be here" and
  the test suite says "you forgot". This is the reason Bean Validation was
  adopted after being evaluated and rejected once: the cost of a hand-written
  guard is not the guard, it is the day somebody forgets one.
- **Only presence moved.** Format stays in `WireParsing` (friendly brackets,
  case-insensitive enums, ISO times, zone ids) and business rules stay in the
  domain value objects. Two mechanisms for two different jobs, never three for
  one.
- **The 422 wording is ours, not the framework's.** Hibernate Validator's
  default messages come from a resource bundle that follows the JVM's locale:
  a Spanish server would answer a Spanish error to an English API. The advice
  builds the message from the violation PATH, so the answer is identical
  everywhere and the nested structure survives (`schedule.windows[0].day is
  required`).
- **The annotation is a test, not a comment.**
  `everyFieldOfTheRequestIsDeclaredRequired` walks each record's fields and
  their presence constraints — including the type arguments of collections,
  which only the ANNOTATED type carries — and fails if a field lacks one.
- **The generic 500 is blank.** No exception class, no message, no stack
  trace, no extra properties: only `500`/`Internal error`/`Unexpected error`.
  The stack trace goes to the ERROR log, where the operator reads it. A
  caller who learns WHICH failure happened learns something about the
  server's internals for free, and the request may be hostile.
- **Verified by test, not by inspection.** `ApiErrorHandlingTest` asserts the
  generic body contains no exception class, no JDBC URL, no host, no project
  name and no stack frames, that the properties are empty, and that two
  different anomalies produce the SAME detail.

## WU13 — Read your own character (and make the WU8 Location true)

| Piece | Package | Content |
|---|---|---|
| Driving port | `application.port.in` | `GetCharacter` — returns the caller's own `Character` |
| Service | `application.service` | `GetCharacterService` — resolves the caller, loads, enforces ownership |
| Not-found factory | `application.error` | `ResourceNotFoundException.characterNotFound()` — ONE message, no parameter |
| Endpoint | `adapters.in.rest` | `GET /api/characters/{id}` → `200` + the character sheet |
| Response DTO | `adapters.in.rest` | `CharacterResponse` — every bracket's standing, keyed `2v2`/`3v3` |

- **Anti-enumeration is now total across three paths.** An unknown id, another
  player's character and a segment that is not even a UUID all produce the same
  404 with detail `Character not found`. A malformed id cannot name a character
  either, and giving it a different status would only add a second answer to
  reason about.
- **The message has exactly one source.** `PublishListingService` previously
  held a private `CHARACTER_NOT_FOUND` constant; duplicating it in the new
  service would have been two places that can drift apart. A static factory on
  `ResourceNotFoundException` means the rule cannot be re-typed wrong.
- **The caller is resolved BEFORE the lookup**, as in publish: an
  unauthenticated request is a 401 whether or not the id exists.
- **`ProblemDetail.instance` echoes the request URI.** Comparing two 404 bodies
  byte-for-byte fails on that field alone, and that is correct behavior, not a
  leak: the URI is the caller's own input. `GetCharacterControllerTest`
  therefore compares the problem map without `instance` — type, title, status,
  detail and the absence of extra properties must all match.
- **The character sheet is not the board projection.** It returns every
  bracket's standing with rating, season and observation date, and no stale
  flags: freshness is a board-read policy, and this endpoint reports observed
  evidence with its dates. `ownerId` stays out here too.
- **This closes the WU8 debt.** `POST /api/characters` has returned
  `Location: /api/characters/{id}` since WU8 while that URL answered 404. The
  header is now true without touching it. Listings still have no read-by-id, so
  their 201 keeps carrying no Location.
- **Boundaries.** No update, no delete, no listing read-by-id, no public view
  of other players' characters — that last one is a product decision, not an
  oversight.

## WU15 — The group handshake (JoinRequest) and the facts of a finished listing

| Piece | Package | Content |
|---|---|---|
| Aggregate | `domain.join` | `JoinRequest` — requester, listing, status, createdAt, expiresAt |
| Status | `domain.listing` | `ListingStatus.COMPLETED`; `LfgListing.complete()` |
| Fact | `domain.listing` | `ListingOutcome` + `ListingOutcomeKind` — what a finished listing was |
| Driving ports | `application.port.in` | `RequestToJoin`, `AcceptJoinRequest`, `RejectJoinRequest`, `WithdrawJoinRequest`, `WithdrawListing` |
| Driven port | `application.port.out` | `ListingLifecycle` — the atomic close, the facts and the request sweep |
| Migration | `db/migration` | `V3__create_join_requests_and_listing_outcomes.sql` |
| Endpoints | `adapters.in.rest` | request / accept / reject / withdraw a request; withdraw a listing |

- **The handshake is not bureaucracy — it is what makes COMPLETED mean
  something (user decision).** Accepting unilaterally would close the owner's
  listings without their consent, which lets anybody empty the board on
  purpose and forge successes. A group forms only when BOTH sides agreed, so
  the success rate is defensible.
- **A request LIVES AS LONG AS THE LISTING.** `expiresAt` is the listing's own
  expiration, copied at creation: no separate TTL, no separate configuration.
  A request to join a listing that dies in three hours dies with it, and the
  request's EXPIRED state and the listing's EXPIRED state are the same instant.
- **There is no "I found a group" button (user decision).** It cannot be
  verified and it can be abused to forge successes. COMPLETED is reachable
  ONLY through an accepted request.
- **Accepting closes EVERY active listing of BOTH players**, in every bracket,
  and writes one fact per closed listing — in ONE transaction, with the
  request marked ACCEPTED. Half a group is worse than no group, and facts
  that disagree with the listings would poison the metrics. Nothing else
  closes: the listings of a third party are untouched, and there is a test
  for exactly that.
- **The owner may not accept their own request (user decision).** It would not
  form a group and it would forge a success: 422, not "close it once".
- **Only the owner may accept or reject; only the requester may withdraw.**
  Anybody else gets the same 404 as for a request that does not exist, because
  the answer must not reveal that it does. A request that is not PENDING is a
  422: the transition already happened.
- **REJECTED and CANCELLED are different facts.** The owner refused and the
  requester gave up are not the same event, and the metrics must tell them
  apart. Withdrawing is not rejecting.
- **`owner_id` is denormalized on `join_requests`, on purpose.** Two rules are
  about the PLAYER, not the character: a player cannot request the same
  listing twice (not even with another character) and a player cannot hold
  more than **3** pending requests. Both are laws of the database:
  `ux_join_requests_pending_per_player_listing` for the first, an application
  check for the second.
- **Why 3 pending requests, and not 1.** A single global pending request
  blocks a player who found no answer for the whole life of the listing (24
  hours). Three keeps the queue short without stranding anybody.
- **Starting to play cancels every pending request of BOTH players** (not just
  of the character that was accepted), in the same transaction as the close.
  The owner is playing too, so anything they were waiting for is irrelevant,
  and anything they were asking for dies with their search.
- **A closed listing takes its pending requests with it.** When a listing
  closes — COMPLETED, CANCELLED by the owner, or EXPIRED — every PENDING
  request aimed at it is cancelled in the same transaction. Otherwise the
  requests of other players stay hanging, waiting for an answer that can
  never come, and keep occupying their slots.
- **Only the requests that are still ALIVE count towards the limit of three.**
  A request whose listing has died is expired by definition, even before the
  retention job marks it: `expires_at > now` is part of the count. Otherwise
  a player with three requests to expired listings would be blocked for
  good. The retention job (WU16) is what marks them EXPIRED.
- **There is ONE way to close a listing.** Every close — the owner pulling
  it, the group forming, the clock running out, and the sweep that frees the
  slot for a new publication — goes through `ListingCloser`. A close that
  skipped it would write no fact (so the listing would never appear in the
  metrics) and would leave its pending requests hanging (so the players who
  asked would keep their slots occupied). Both failures are invisible in the
  UI: the board looks fine and the numbers are quietly wrong. One door makes
  them impossible.
- **The sweep that frees the slot writes the fact too.** The `expireDue`
  update inside `JpaListings.publish` used to mark the due row EXPIRED and
  nothing else: the listing would never reach the metrics, because the
  retention job would find it already EXPIRED and skip it. It closes through
  the shared closer now, in the same transaction as the new publication.
- **Derived Spring Data finders are a trap for ranges.**
  `findByCharacterIdAndBracketAndStatusAndExpiresAt(…, Instant)` reads as an
  EQUALITY on `expires_at`, not as `<=`. The sweep compiled, ran, found no
  rows and let the new publication hit `ux_lfg_listings_active`, so the bug
  looked like a uniqueness problem instead of a query problem. Any range
  predicate gets an explicit `@Query`; the method name is not documentation.
- **The unit close is per entity; the WU16 job batches the pages.**
  `ListingCloser.close` issues one `UPDATE`, one request sweep and one fact
  insert per listing, which is right for every request-driven close (one
  listing, or the handful of a player's active ones). The retention job walks
  every overdue listing in the database, so it calls the same close inside a
  page of bounded size, one transaction per page: the single door stays, the
  number of rows per commit changes. See WU16.
- **The facts outlive their subjects.** `listing_outcomes` has NO foreign key
  on the listing (the fact must outlive the row the retention job deletes) and
  a nullable `character_id` with `ON DELETE SET NULL` (the fact must outlive
  the player). The metrics read the facts, never the listings.
- **The success rate is CONSERVATIVE (user decision).** It counts only the
  groups formed inside the app, so it under-reports: groups formed outside
  the app are invisible. It will be revisited with real data.
- **The 3v3 simplification (user decision).** In 3v3 the listing means "one
  player is missing", but the FIRST acceptance closes the search: the group of
  three is formed out of the app, in voice chat. There is no Group entity and
  no lobby yet.
- **Tests.** `JoinRequestTest` covers the aggregate (one-shot transitions,
  expiry at the exact instant). `RequestToJoinServiceTest` covers your own
  listing (422), absent vs foreign character (the same 404), a closed
  listing, the duplicate request and the limit of three.
  `AcceptJoinRequestServiceTest` covers the two rules that protect COMPLETED
  (own listing → 422, not the owner → 404), the close of both players, the
  player without a listing, the third party that is NOT touched and the
  cancellation of the requester's pending requests. `JpaListingLifecycleIT`
  proves the same against real PostgreSQL, including that a fact survives the
  deletion of its character.

## WU16 — The retention sweep

A daily job at 04:00 UTC marks overdue ACTIVE listings EXPIRED, writes their
fact, cancels their pending requests, and deletes the rows that are no longer
ACTIVE whose `expires_at` is older than 30 days. The board is already correct
without it (the query filters overdue rows out), so this is hygiene, not a
correctness blocker — but without it a listing nobody touches would stay
ACTIVE for ever (never reaching the facts) and its row would never be deleted.

- **Three passes, and the order is the rule.** Close the overdue listings
  first, then expire the requests left over from listings that died in an
  earlier run, then delete. Deleting before closing would remove a row whose
  fact was never written; expiring requests before closing would mark as
  EXPIRED what the close should have marked CANCELLED. The order is asserted
  in `RetentionSweepServiceTest`.
- **Every close still goes through `ListingCloser`.** The sweep does not flip
  a status in bulk: it closes one listing at a time through the same door as
  a withdrawal or a formed group, so each listing writes its EXPIRED fact and
  cancels its pending requests. A bulk UPDATE would be faster and would
  silently lose both.
- **One page per transaction.** `ListingRetention` takes a `pageSize` and
  reports how many rows it handled; the service loops until a page comes back
  short. A full backlog becomes many short commits instead of one long
  transaction, and a crash replays only the page that failed. The whole run is
  idempotent: a closed listing is no longer ACTIVE, a deleted row is gone, and
  an EXPIRED request is not PENDING.
- **Only ACTIVE survives the deletion (user decision).** Every closed status —
  CANCELLED, EXPIRED and COMPLETED — is deleted once its `expires_at` is
  older than the window. The history of the success rate lives in
  `listing_outcomes`, and every COMPLETED row already wrote its fact there;
  keeping the listing too would duplicate the source of the metrics and grow
  the board table without a ceiling. The facts survive the deletion by design
  (`listing_outcomes` has no foreign key on the listing); `join_requests`,
  `listing_languages` and `listing_windows` die with it through the database
  cascade. The predicate is written as `status <> ACTIVE`, not as a list, so
  the rule is the one stated: the board table only holds what is on the board.
- **The window and the cron are server-owned configuration.**
  `pvp.retention-after-expiry` (default `P30D`) and `pvp.retention-cron`
  (default `0 0 4 * * *`, evaluated in UTC). A zero or negative window fails
  the boot, like the TTL: a misconfiguration must kill the process, not the
  data.
- **ONE INSTANCE, NO LOCK (documented assumption).** Two instances running the
  job at the same instant would both try to close the same rows; the partial
  unique index and the fact primary key would turn that into an error and a
  rollback, not duplicated history. The safe multi-instance design is a
  PostgreSQL advisory lock or `FOR UPDATE SKIP LOCKED`; it goes in the job the
  day there is a second instance, not before.
- **The job logs its report, not its rows.** One INFO line per run with the
  three counters. A failure propagates and Spring logs it; the pages already
  committed stay committed, which is the point of the page size.
- **Tests.** `RetentionSweepServiceTest` covers the drain (full pages, a short
  page, an exact multiple), the order, the cutoff and the fail-fast
  configuration. `JpaListingRetentionIT` covers the same against real
  PostgreSQL: an overdue listing closed with fact and cancelled requests, a
  live one untouched, two pages drained, deletion past the window with the
  fact kept, deletion inside the window refused, a COMPLETED listing deleted
  past the window with its fact surviving, requests marked EXPIRED, and the
  whole wired sweep in one run.

## WU17 — Real players

Until now the auth stub accepted ANY UUID as `X-Player-Id`, so
`characters.owner_id` pointed at a value that existed nowhere. WU17 makes the
owner a row: the `players` table, a sign-up use case, and the foreign key that
turns "an owner" from a claim into a fact. It is deliberately small — it exists
to make the Battle.net login of WU18 a drop-in replacement, not a rewrite.

- **The header is a CLAIM, and the adapter verifies it.** `X-Player-Id` is
  still the dev identity, but now a well-formed UUID that names no player is a
  401 (`Unknown player`), not an identity. The check lives in
  `DevCurrentPlayerProvider`, because authentication must guarantee that the
  identity it returns is real: if an unknown id travelled through the use cases
  it would only fail at the foreign key, as a 500. Downstream code receives a
  genuine owner. Cost: one primary-key lookup per request.
- **401, not 404.** The caller controls this header, so naming the problem
  leaks nothing that is not already theirs; and "not authenticated" is exactly
  what an id with no account means. A 404 would suggest a resource they could
  find.
- **Sign-up is the only unauthenticated write.** `POST /api/players` takes
  `username` and `battleTag`; there is no identity to present before the
  identity exists. `GetCurrentPlayer` (`GET /api/players/me`) is the only read
  of an account: a battle tag is personal contact data, so there is no
  `GET /players/{id}` to harvest them. `POST` answers 201 with `{ "id": ... }`
  and NO `Location`, because the created resource has no public address.
- **Duplicates are 422, like every other invalid input.** The pre-checks are
  advisory UX; the law is in the database:
  `ux_players_username on lower(username)` (a handle is case-insensitive) and
  `ux_players_battle_tag` (the value object already folds the tag). A race past
  the pre-check still ends as a 500 today, exactly like the character
  uniqueness rule.
- **`characters.owner_id` is a foreign key `ON DELETE CASCADE`.** Deleting a
  player deletes their characters; their `listing_outcomes` survive with
  `character_id` set to NULL, which is the fact table's existing promise.
  `join_requests.owner_id` gets the same constraint: leaving one player
  reference unenforced while the other is enforced would be a half-true schema.
- **The FK is added in TWO steps: `NOT VALID` (V4), then `VALIDATE` (V5).**
  Every character registered before WU17 has an owner id that no player
  holds, because there was no players table to hold it. Backfilling would mean
  inventing a username and a battle tag — fabricating identity — so V4
  declares the constraint `NOT VALID`: it is enforced for every NEW write (the
  rule starts now) and the historical rows are left untouched. V5 then runs
  `alter table ... validate constraint ...`, which scans each table once and
  promotes the constraint to fully trusted.
  The split is not ceremony. `ADD CONSTRAINT ... NOT VALID` takes a SHARE ROW
  EXCLUSIVE lock and does no scan; `VALIDATE CONSTRAINT` takes a SHARE UPDATE
  EXCLUSIVE lock — reads and writes keep flowing — and does the scan. One
  combined statement would hold the stronger lock for the entire scan, which is
  the classic way to freeze a large table in production. With no production
  data the scan is free, but the discipline is the lesson. V5 fails loudly if a
  database still has orphans: a constraint that lies is worse than a failed
  boot.
  A development database that predates WU17 can be cleaned before starting:
  ```sql
  delete from join_requests r
   where not exists (select 1 from players p where p.id = r.owner_id);
  delete from characters c
   where not exists (select 1 from players p where p.id = c.owner_id);
  ```
  The second delete cascades to the character's listings, requests, specs and
  standings; the facts survive with `character_id` set to NULL. Verified by
  `JpaPlayersIT.aCharacterCannotBeSavedForAnOwnerThatIsNotAPlayer` for the
  enforcement of new rows, and by every IT container validating an empty table
  at boot for the promotion.
- **`UnauthorizedException` moved to `application.error`.** More than one layer
  needs to say "this identity is not a player": the security adapter when the
  header is missing, the application when the player has vanished. Keeping it
  in the adapter would force the application to import an adapter, and
  dependencies point inward.
- **Tests.** `RegisterPlayerServiceTest` (identity returned, duplicates
  rejected, nothing saved on failure), `GetCurrentPlayerServiceTest` (unknown
  identity is 401), `RegisterPlayerControllerTest` (201 with no Location, 422
  for a missing field / malformed tag / taken handle, 400 for broken JSON),
  `CurrentPlayerControllerTest`, `DevCurrentPlayerProviderTest` (an unknown but
  well-formed UUID is 401), `JpaPlayersIT` (round-trip, case-insensitive handle
  index, battle-tag index, the FK refusing an orphan character) and
  `PlayerFlowIT` (sign-up → `/me` → register a character, all through the real
  adapter and the real database).

## WU18 - Battle.net OAuth2

WU17 made the player a row. It did not make "who is calling" true: the header
was a claim the server could only check against the players table, so anybody
holding a player id could speak as that player. This unit replaces the claim
with a login and retires the field the claim lived in.

This unit was reviewed and hardened before it landed. The corrections are part
of the decision, not an amendment to it: the development adapters are DELETED
rather than parked behind a profile, a player cannot exist without a Battle.net
account, and the battle tag is contact data instead of an identity. The
readers of the earlier WU17 sections will find `X-Player-Id` described there;
that is what was true at the time, and the point of the spec is to record the
journey, not to hide it. What is true NOW is in this section.

- **The backend is a confidential client, and the session is the credential.**
  The authorization-code flow runs on the server: the browser is redirected,
  Battle.net returns a code, the backend exchanges it using the client secret,
  and the result is an `HttpSession` cookie. The alternatives were considered
  and refused: a self-issued JWT would mean owning signing keys, rotation and
  revocation (a real logout stops being free) to solve a scaling problem this
  product does not have - retention already assumed a single instance; and a
  public SPA client would put a Battle.net access token in JavaScript, where
  any XSS becomes an account takeover, plus one upstream call per request or a
  cache nobody asked for. The session is the honest shape of "one server, one
  browser".
- **Identity is the Battle.net `sub`, not the battle tag.** A battle tag can be
  renamed - Battle.net sells the service - and keying the account on it would
  turn a rename into a new player with none of the old player's characters.
  `battle_net_id` is stored next to the tag, never instead of it: the tag is
  contact data the product needs, the id is the key that never moves.
- **The handle is gone.** `username` existed to be a LOGIN identity, and its
  unique index existed to stop two people choosing the same one. Once
  Battle.net answers "who are you", the handle has no job and its index guards
  a rule nobody states any more. Column, index, value object, `usernameTaken`
  and `POST /api/players` all disappear with it. The account is now the battle
  tag, which the provider hands over already verified.
- **A login has two outcomes, and the order is the rule.**
  `SignInWithBattleNetService` asks: does this ACCOUNT exist (recover it, and
  rewrite the tag if it changed)? No (first login: create the player). There
  used to be a third outcome - "this TAG exists without an account, claim that
  row" - and it is gone, because a player without an account cannot exist any
  more and because the battle tag is mutable: it cannot identify a row.
- **A rename is not a new player.** If the provider reports a different tag for
  a known account, the tag is rewritten on the same row. It never collides with
  anybody: battle tags are not unique, so the rewrite is a plain update.
- **Only the board is public.** `GET /api/board` needs no session: it is the
  product, it shows nothing private, and requiring a login to look at it would
  require an account to decide whether the board is worth an account.
  Everything else requires a session. The login handshake itself
  (`/oauth2/**`, `/login/**`) and the container's `/error` dispatch are exempt
  for obvious reasons.
- **An unauthenticated API call gets a 401, never a redirect.** Spring's OAuth2
  support defaults to redirecting the browser to the provider, which is right
  for a website and wrong for a JSON API: a `fetch()` that follows the redirect
  receives an HTML login page instead of a status it can branch on. A custom
  entry point writes `application/problem+json` in the filter chain, before any
  message converter exists, which is why the four fields are assembled by hand.
  The redirect still exists; it is just explicit, at
  `GET /oauth2/authorization/battle-net`.
- **CSRF is disabled, on purpose, and the reason is the cookie.** The session
  cookie is `SameSite=Lax`, so a cross-site POST arrives without it and is
  anonymous; and no state-changing route is a GET, so Lax's one hole (top-level
  GET navigation) has nothing to exploit. `Strict` would have been stronger and
  would have broken the flow: the OAuth callback is a cross-site GET navigation
  that must carry the session holding the pending authorization request. This
  is the decision to revisit in WU20, when the SPA gets its own origin and the
  cookie becomes cross-site - at that point CSRF protection comes back.
- **THE GET HALF OF THAT PROMISE IS CHECKED BY THE BUILD.** A cookie attribute
  is a browser's promise; a route is ours, and a route can change by
  accident. One `@GetMapping` that saves something and the promise breaks.
  `GetEndpointsDoNotChangeStateTest` is a STRUCTURAL rule: ArchUnit reads the
  bytecode and the annotations and answers "could this method possibly
  write?", not "did it write?". It has two halves: a GET handler must not CALL
  a writing method, and it must not even DEPEND ON a writing port, because a
  dependency is one refactor away from a call. The writing methods of this
  project are recognised by name - `save`, `publish`, `delete`, `expire`,
  `complete`, `withdraw`, `close` - and the reading ones (`find`, `has`,
  `current`, `already`, `pending`, `count`) win over them. That list lives in
  one place, so adding a writing method with a new name is a decision somebody
  has to make in the test rather than a silent hole.
  The honest limit: a static rule cannot follow a call through a service into
  a repository. That is why the RUNTIME half exists too - the read paths run
  in a `READ ONLY` transaction and PostgreSQL refuses the write. Structure
  catches the mistake before the code runs; the database refuses it if the
  structure is wrong.
  The rule was verified to BITE: a GET that called `Players.save` was written
  on purpose, and the build failed naming the handler and the method.
- **The read paths run in a transaction the database opened as READ ONLY.**
  `ReadOnlyTransactions` is the one place that builds it: a
  `TransactionTemplate` with `setReadOnly(true)`, which the PostgreSQL driver
  turns into `BEGIN READ ONLY` (its default `readOnlyMode` is `transaction`).
  An explicit write inside it is rejected by the database itself with
  `cannot execute INSERT in a read-only transaction`.
  It is a class and not an annotation because the adapters are `final` by
  convention and `@Transactional` needs a proxy.
  Two things this buys, and the second one is not about safety at all: the
  database refuses an accidental write, AND the board query - five
  statements - sees ONE snapshot. Without a transaction those five ran in
  auto-commit, each in its own snapshot, so two of them could see two
  different versions of the database.
  A read-only transaction that finds an existing transaction JOINS it, so
  asking a question while writing (`hasActiveFor` inside `publish`) still
  works: the inner flag is ignored and the outer transaction rules.
  `ReadOnlyTransactionsIT` pins the promise empirically - it asks the
  database to write and checks that the DATABASE says no. A flag that
  silently stopped reaching the driver would leave the suite green and the
  promise broken.
- **The application does not START without the Battle.net client.** An
  application that cannot authenticate should not pretend to be running.
  WU18 shipped with a `not-configured` placeholder, which meant the
  application booted happily and the LOGIN failed - the operator saw a broken
  login and had to guess that the client was never configured.
  The reason a YAML default could not do the job: Spring Boot resolves a
  missing `${BATTLE_NET_CLIENT_ID}` by leaving the placeholder TEXT in place
  (`resolvePlaceholders`, not `resolveRequiredPlaceholders`), so the client
  registration was built with a literal `${BATTLE_NET_CLIENT_ID}` as its id.
  A default is a value that LOOKS like a credential and is not one.
  So the check is explicit: `BattleNetClientConfiguration` refuses a value
  that is empty or still an unresolved placeholder, and the boot fails with
  the name of the variable in the message. The same pattern the TTL and the
  retention window already use: a misconfiguration must kill the boot, not
  the first request.
- **The development adapters are DELETED, and that is the user's decision, not
  an accident.** `DevCurrentPlayerProvider`, `DevSecurityConfig`,
  `DevPlayersController`, `RegisterDevPlayer` and `RegisterDevPlayerService`
  are gone from the codebase, together with their tests. The reason, in the
  user's own words: **we do not want anything in the deployed artifact that
  lets somebody impersonate a player.** A `@Profile("dev")` backdoor was the
  previous answer, and a profile is a mitigation rather than a guarantee: it is
  one environment variable a deployment happens to carry away from being
  production. The only way to be sure is for the code not to be there. There is
  no `dev` profile any more, no `X-Player-Id` header, and no
  `POST /api/dev/players`.
  The tests that used the header now sign in the way production does:
  `oauth2Login()` for the provider handshake plus the internal player id in the
  session, which is exactly the pair the real filter chain and the real
  adapter need.
- **A player exists only because somebody logged in with Battle.net.** The
  `sub` claim is the identity; the battle tag is contact data that the board
  shows and that the login refreshes every time. `battle_net_id` is NOT NULL
  and there is no second door: `Player.registerWithoutAccount`,
  `Player.rehydrateUnlinked` and `Player.linkTo` are deleted, and so is the
  "claim by battle tag" branch of `SignInWithBattleNetService`. A login has
  two outcomes now, not three: the account comes back (and its tag is
  refreshed if it changed), or the player is born.
  The reason is not tidiness: the application has never been deployed, so there
  are no historical rows to protect, and the battle tag is MUTABLE. It cannot
  be an identity - Blizzard reclaims battle tags and two accounts can carry the
  same one - so keying a player on it would turn a rename into a second human.
  A login without a `sub` is a 401: a provider that answers without it cannot
  identify anybody, and guessing would be inventing identity.
- **The `sub` is stored verbatim, as a string.** OpenID Connect defines `sub` as
  a string, and today's Battle.net happens to send digits. A `bigint` column
  would be a promise the provider never made, and a broken one costs every
  account already stored; it would also turn a non-numeric `sub` into a
  `NumberFormatException` and a 500 where the answer should be a 401. The
  column is `text`, and the value object stores the claim as it arrives.
- **The battle tag is NOT unique any more.** Blizzard reclaims battle tags, so
  the old unique index would have rejected a perfectly legitimate pair of
  players. The index becomes a plain one: the tag is searchable contact data,
  never an identity, and a rename never collides with anybody.
- **V6 is rewritten, not amended.** The migration has never been committed or
  applied anywhere, so it is edited into its final shape instead of leaving a
  nullable column and a follow-up migration to fix it: `battle_net_id` is
  `text not null` from the start, and the battle tag index loses its
  uniqueness in the same migration.
- **The Battle.net token lives in the session and dies with it.** Nothing
  sensitive reaches the database and there is no key to rotate. WU19 imports
  characters only while the player is signed in, which is when they ask for it.
  A background sync would need a stored, encrypted refresh token - the most
  dangerous piece of the whole project - and it is deliberately not built.
- **The session is a row in the database, not an object in the heap.** While it
  lived in memory, a deploy logged everybody out and a second instance could
  not see the sessions of the first. The session IS the credential, so the one
  thing that must survive a deploy is the credential; a rolling restart is a
  normal thing to do to a server. Spring Session JDBC keeps it in the database
  we already have, in `spring_session` and `spring_session_attributes` (V7).
  The schema is COPIED into the migration rather than letting the application
  run Spring Session's own script: the rule in this project is that Flyway
  owns the schema, and `spring.session.jdbc.initialize-schema: never` keeps
  that promise from the other side. A table the application can create is a
  table two versions of the application can disagree about.
  The dependency is `spring-boot-starter-session-jdbc`, NOT the bare
  `org.springframework.session:spring-session-jdbc` jar: Boot 4 split its
  auto-configurations into modules, and adding only the Spring Session jar
  leaves the application with NO `SessionRepository` at all - the session then
  quietly stays in the heap, and the tests pass while proving nothing.
- **The session cookie is `Secure` by default.** `SESSION_COOKIE_SECURE`
  defaults to `true`, so a deployment that forgets to configure it does not
  hand the credential out over plain HTTP. The cost is honest and visible:
  local development over `http://localhost` needs
  `SESSION_COOKIE_SECURE=false`, because a browser refuses to send a `Secure`
  cookie over plain HTTP. That is the right way round: the insecure setting
  has to be asked for by name.
- **One constant holds the session attribute.** `BattleNetSignInHandler` writes
  `pvp.current-player-id`, `SessionCurrentPlayerProvider` reads it, and both use
  `SessionCurrentPlayerProvider.PLAYER_ID_ATTRIBUTE`. Two strings with the same
  spelling drift; one constant with two readers cannot. The attribute holds a
  `UUID`, not the `PlayerId` record, so the session stays serializable - and
  Spring Session serializes it as bytes, which is why the test that proves the
  session is persisted has to deserialize it to check it.
- **The handler converts claims, not identities.** It reads `sub` and
  `battletag` (falling back to `preferred_username`), and if either is missing
  it answers 401 instead of guessing: an account id invented at the edge would
  be fabricated identity, the same sin the FK backfill refused to commit. Both
  values are read as strings because Jackson binds a JSON number to an
  `Integer` and Battle.net has used both shapes. A `sub` that is blank is also
  a 401, for the same reason.
- **No discovery document.** The provider's endpoints are configured
  explicitly instead of `issuer-uri`, so application startup does not depend on
  reaching Battle.net. A test run has no business calling a third party.
- **V6 is a plain schema change, not a two-step one.** There is no new foreign
  key here, so there is nothing to validate: add the `battle_net_id` column,
  put a unique index on it, drop the username index, drop the column, and drop
  the battle tag's uniqueness.
- **A known, bounded race.** Two callbacks arriving at once for a first-time
  account can both find nothing and both insert; the unique index rejects the
  loser, and that request fails while the retry wins. It is left visible rather
  than hidden: the honest fix is an `insert ... on conflict` upsert in the
  adapter, which is a change worth making when a real user reports it, not
  before.
- **Tests.** `SignInWithBattleNetServiceTest` (create, recover, rename, two
  accounts sharing a battle tag), `BattleNetSignInHandlerTest` (claims →
  session, numeric `sub`, non-numeric `sub` kept verbatim, fallback claim,
  refusal without claims or with a blank one),
  `SessionCurrentPlayerProviderTest` (session attribute, missing attribute, no
  request), `JpaPlayersIT` (the unique index on the account id, two players
  with the same battle tag, the NOT NULL column, the FK),
  `JdbcSessionIT` (the session is a row in PostgreSQL, it carries the player
  id, its attributes die with it),
  `PlayerFlowIT` (a signed-in player end to end, with `oauth2Login()` plus the
  internal player id), `SecurityIT` (public board, 401 problem+json, a
  session reaching the use case),
  `GetEndpointsDoNotChangeStateTest` (no GET can reach a writing port, and
  the rule finds the ports it is supposed to find),
  `ReadOnlyTransactionsIT` (the database refuses the write, the read works,
  and the write outside the transaction still works),
  `BattleNetClientConfigurationTest` (an empty or unresolved credential is
  refused) and `MissingCredentialsIT` (the real application does not boot
  without them). The WU17 tests that named a handle are gone with the handle,
  and the dev tests are gone with the dev adapters.
- **The tests sign in through a FILTER, not through a session object.** Spring
  Session replaces the servlet session with its own, and its filter runs before
  anything else. A `MockHttpSession` handed to MockMvc is a DIFFERENT session
  from the one the adapter reads and from the one Spring Session persists, so
  an attribute set on it lands in a session nobody ever looks at - and the
  test passes while proving nothing. `AuthenticatedCaller` marks the request
  with the player id and a filter, registered between Spring Session's filter
  and Spring Security's chain, moves it into the session that counts.
- **What the suite cannot prove.** The handshake with Battle.net itself. That
  is a network round trip to a third party, and CI has no business making it;
  everything on our side of the redirect is covered.

## Error strategy (decided before the application layer)

Errors have three audiences, and each is served at a different layer:

| Audience | Needs | Served by |
|---|---|---|
| The caller | a clear message to correct and retry | the exception message → `422` for invalid data or `404` for a missing or inaccessible resource (`ProblemDetail`, RFC 9457, from the REST advice in `adapters.in.rest`) |
| The operator | logs of anomalies | the catching edge — never the domain |
| The developer | the stack trace of the unexpected | `500` + ERROR with correlation id |

- The domain THROWS, it never logs: it cannot know whether a violation is
  expected or anomalous (the caller's context decides), and logging is an
  infrastructure side effect. Enforced by ArchUnit — `org.slf4j..` is already
  forbidden in `..domain..`.
- The failure families, already built: `Objects.requireNonNull` (NPE) means a
  bug in the calling code → 500; `DomainRuleViolationException` means invalid
  data → 422; `ResourceNotFoundException` means the aggregate is absent OR the
  caller may not act on it → 404, with one message so existence never leaks.
  The dev auth adapter adds `UnauthorizedException` → 401. Messages carry the
  offending value ("got: 0", "ROGUE_ASSASSINATION ... MAGE") except when that
  would reveal a resource the caller cannot access.
- A failed validation is NOT log-worthy — it is normal business. Logging every
  400 at ERROR turns bot traffic into noise that buries real signals.
  Validation failures become a METRIC (count per violated rule: which field
  frustrates players the most), not a log line.
- Once `adapters.in.rest` exists: `DomainRuleViolationException` → 422
  ProblemDetail at DEBUG; unexpected → 500 + ERROR with correlation id (MDC);
  use-case events (character registered, listing published, later sync
  results) → INFO, structured; JSON logs in production, console in dev.

## Testing standard

Mirror `CharacterNameTest`: happy path, every boundary (min, max), one test per
invalid category. Aggregates additionally test factory rules, identity equality
(two instances, same id, different field → equal), and state transitions
(`cancel` twice → exception; `expireIfDue` before expiry → `false` and still
`ACTIVE`). No mocks — if you feel you need one in a domain test, the design is
wrong.

## Definition of done

- [ ] `./mvnw test` green after every work unit
- [ ] Purity check prints nothing
- [ ] Each work unit reviewed with me before starting the next

## Out of scope (do not build)

The domain-phase exclusions — ports, repositories, use cases, controllers —
are no longer excluded: that phase is complete. Still out of scope until their
own steps: the Blizzard API client (sync step), stored Battle.net refresh
tokens, CSRF protection for a cross-origin client, React.

## Next step

WU12 (REST exposure, keyset pagination, bean wiring and the configured TTL),
WU13 (read your own character, with total anti-enumeration), WU14 (declared
presence + a blank generic 500), WU15 (the group handshake and the facts of a
finished listing), WU16 (the daily retention sweep), WU17 (real players and the
owner foreign key) and WU18 (Battle.net login and the session that carries it)
are done.

Next: WU19 — import characters and their observed facts from Blizzard. The
access token is already in the session (scope `wow.profile` was requested at
login), so the import is a use case the signed-in player triggers; nothing has
to be stored to make it possible.

Then: WU20 (the React client: board, publish, request, accept) and WU21 (a
public deployment for the CV). WU20 owns two debts this unit left explicit:
CSRF protection once the cookie becomes cross-site, and the login redirect that
currently points at `/`.

Still open afterwards: the lobby with chat (it needs a Group entity, which
does not exist yet) and a listing read-by-id resource.

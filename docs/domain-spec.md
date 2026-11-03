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
| `Player` | `domain.player`, **class** | Stub-phase aggregate root. State: `PlayerId id`, `Username username`, `BattleTag battleTag`. Same pattern as `Character`: two doors (`register` generates the id, `rehydrate` receives it), no setters, `equals`/`hashCode` **by id only** — a record here would give structural equality, and a player who changes battle tag would stop being the same player. The battle tag is required: it is the ONLY contact channel in the product. Battle.net OAuth later enriches this concept, never replaces it |

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
| Dev auth | `adapters.out.security` | `DevCurrentPlayerProvider` reads `X-Player-Id` via the request context; missing/garbage/no-context → `UnauthorizedException` |
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
- **Retention is a scheduled job, not lazy cleanup (user decision, later
  WU).** A daily job will mark overdue ACTIVE listings EXPIRED and delete
  CANCELLED/EXPIRED rows older than 30 days. Publishing already expires the
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
- **The unit close is per entity; the WU16 job will need it batched.**
  `ListingCloser.close` issues one `UPDATE`, one request sweep and one fact
  insert per listing, which is right for every close that happens today (one
  listing, or the handful of a player's active ones). The retention job walks
  every overdue listing in the database, so if the volume grows it must call
  the same close in pages — chunked transactions — instead of one row at a
  time: the single door stays, the cost per transaction changes.
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

## WU16 — The retention sweep (still to come)

A daily job marks overdue ACTIVE listings EXPIRED, writes their fact, and
deletes the CANCELLED/EXPIRED rows whose `expires_at` is older than 30 days.
The board is already correct without it (the query filters overdue rows out),
so this is hygiene, not a correctness blocker.

It must close through `ListingCloser`, so every listing it expires writes its
fact and cancels its pending requests. Today `close` works one entity at a
time, which is fine for the handful of rows a publication or a group touches;
the job walks the whole table. Start simple (one transaction per page of
listings, reusing `close`), and if the volume grows, page over the overdue ids
and keep chunking transactions — the single door does not change, only how
many rows travel through it per commit.

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
own steps: the Blizzard API client (sync step), real auth beyond the
`X-Player-Id` stub, React.

## Next step

WU12 (REST exposure, keyset pagination, bean wiring and the configured TTL),
WU13 (read your own character, with total anti-enumeration), WU14 (declared
presence + a blank generic 500) and WU15 (the group handshake and the facts
of a finished listing) are done.

Next: WU16 — the retention sweep. A daily job marks overdue ACTIVE listings
EXPIRED, writes their EXPIRED fact and deletes the CANCELLED/EXPIRED rows
whose `expires_at` is older than 30 days. The board is already correct
without it (the query filters overdue rows out), so this is hygiene, not a
correctness blocker.

Still open afterwards: the lobby with chat (it needs a Group entity, which
does not exist yet) and a listing read-by-id resource.

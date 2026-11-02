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
- **Read policy is split from query execution.** `SearchBoardService` owns
  time, season and freshness; `BoardListings` owns active/unexpired filtering,
  optional predicates and recency ordering. The future SQL adapter must prove
  those behaviors against real Postgres. Loading everything and filtering in
  Java would waste the WU9 relational design.
- WU10 is application-layer only: no listing tables, REST endpoints or Spring
  wiring yet. Pagination must be bounded before the board is exposed publicly.

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

WU10 (publish and search use cases) is done.
Next: WU11 — listing persistence and the real board query in SQL, proven
against real Postgres: expiry sweep, partial unique index, optional filters
and deterministic recency order.

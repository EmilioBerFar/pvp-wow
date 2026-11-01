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
| `Realm` | `domain.character` | `record(String value)` | Trim; collapse inner whitespace runs to one space; lowercase. `"Sanguino"` equals `"sanguino"`. Display casing is a render concern, not a domain concern |
| `Region` | `domain.character` | enum | `EU`, `US`, `KR`, `TW`, `CN` — rated PvP is cross-realm within a region only |
| `WowClass` | `domain.character` | enum | 13 constants (see below) |
| `Specialization` | `domain.character` | enum | 39 constants, each carrying its `WowClass` AND its `Role`; static `forClass(WowClass)` returns the `Set` of its specs (see below) |
| `Role` | `domain.character` | enum | `TANK`, `HEALER`, `MELEE_DPS`, `RANGED_DPS` — the melee/ranged split is the first question when building an arena comp; the classic 3-way split is too coarse. Roles belong to specs, never to classes |
| `Rating` | `domain.character` | `record(int value)` | `0..3500` inclusive. Named `Rating` (visible CR), not `Mmr`: what players report by hand is their CR; MMR is Blizzard's hidden matchmaking value |
| `ItemLevel` | `domain.character` | `record(int value)` | `1..1000` inclusive. Deliberately loose — ilvl caps change every season and expansion, and tight validation rots. Required on every character: gear determines arena readiness |

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
`WowClass wowClass`, `ItemLevel itemLevel`, `Set<Specialization> specs`,
`Map<Bracket, Rating> ratings`.

- Static factory `register(ownerId, name, realm, region, wowClass, itemLevel,
  specs, ratings)` — the only way to create one.
- Factory rules: `specs` non-empty AND every spec belongs to `wowClass`
  (hint: `Specialization.forClass(wowClass).containsAll(specs)`); defensive
  immutable copies of `specs` and `ratings`.
- `ratings` may be empty or partial — a rating per bracket is optional; an
  unranked bracket simply has no entry (`ratingFor` returns empty). The search
  use case (later step) must define how unranked listings sort and how
  item-level proximity filters — gear gaps matter in rated PvP.
- `equals`/`hashCode` by `id` only.
- Queries: `ratingFor(Bracket)` → `Optional<Rating>`, `plays(Specialization)` →
  `boolean`, `roles()` → derived `Set<Role>` from `specs` — computed on demand,
  never stored.
- `armoryUrl()` → derived `String`:
  `https://worldofwarcraft.blizzard.com/{locale}/character/{realm-slug}/{name}`
  where locale maps from region (EU→es-es, US→en-us, KR→ko-kr, TW→zh-tw,
  CN→zh-cn) and realm-slug is the normalized realm with spaces as hyphens and
  apostrophes dropped ("Los Errantes"→`los-errantes`). Never stored, never
  user-entered. Before closing WU2, verify the format against YOUR OWN real
  character in the browser — you play WoW, you hold the ground truth.

## WU3 — Player

| Class | Kind | Rules |
|-------|------|-------|
| `Username` | `domain.player`, `record(String value)` | Trim; 3–24 chars; only letters, digits, `.`, `_`, `-`; must start with a letter |
| `BattleTag` | `domain.player`, `record(String value)` | Battle.net contact identity — account-level, not per character. Trim; lowercase the name part. Format: 3–12 alphanumerics, `#`, 4–5 digits (e.g. `emilio#12345`) |
| `Player` | `domain.player`, `record(PlayerId id, Username username, BattleTag battleTag)` | Stub-phase aggregate. The battle tag is required: it is the ONLY contact channel in the product. Battle.net OAuth later enriches this concept, never replaces it |

## WU4 — Listing value objects

| Class | Kind | Rules |
|-------|------|-------|
| `Bracket` | `domain.listing`, enum | `TWO_VS_TWO` (teamSize 2), `THREE_VS_THREE` (teamSize 3) |
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

Ports, repositories, use cases, controllers, the synergy JSON, Flyway, auth,
Blizzard API, React.

## Next step

Start WU1. When `./mvnw test` is green, ping me for review.

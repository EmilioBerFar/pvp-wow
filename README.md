# pvp-wow

LFG board for World of Warcraft arena PvP (2v2 / 3v3): players register their
characters, publish availability for a bracket, and find partners ranked by
class synergy.

Personal learning project: hexagonal architecture on Spring Boot, PostgreSQL,
Docker Compose. React frontend comes later. Phase 1 has no Blizzard API —
characters are registered by hand.

## Quick path

```bash
docker compose up -d        # PostgreSQL (needed from the persistence step on)
./mvnw test                 # build + tests (Maven wrapper, nothing to install)
./mvnw spring-boot:run      # API (once the REST adapter exists)
```

`JAVA_HOME` must point to JDK 25 (Temurin). One-time setup on PowerShell:

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME',
  'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot', 'User')
```

## Architecture

Hexagonal (ports & adapters). The domain has zero framework dependencies.

```
com.pvpwow.lfg
├── domain          ← pure model: aggregates, value objects, domain rules
├── application     ← use cases + ports (in / out)
└── adapters        ← rest (in) · persistence / synergy / security (out)
```

Each package carries a `package-info.java` documenting its role and its
dependency rule.

See `docs/domain-spec.md` for the Phase 1 domain model and the current work
units.

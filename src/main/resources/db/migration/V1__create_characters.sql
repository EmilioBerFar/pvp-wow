-- Characters: the aggregate root plus its two value collections.
--
-- The unique index on (region, realm, name) is THE law of duplicates: the
-- application-level pre-check is advisory UX, and two simultaneous
-- registrations can only be stopped here.
--
-- owner_id carries no foreign key yet ON PURPOSE: there is no players table
-- and no RegisterPlayer use case, so a FK would reject every insert. The dev
-- auth stub accepts any UUID as X-Player-Id. The FK arrives with the players
-- table, not before.

create table characters (
    id               uuid                     primary key,
    owner_id         uuid                     not null,
    name             varchar(12)              not null,
    realm            varchar(255)             not null,
    region           varchar(255)             not null,
    wow_class        varchar(255)             not null,
    item_level       integer                  not null,
    gear_observed_at timestamp with time zone not null
);

-- The board stores display case ("Frostbolt") but WoW will not let you own the
-- same toon twice by recasing it, so the law compares case-insensitively.
-- A functional index keeps the promise instead of leaving it to the app.
create unique index ux_characters_natural_key
    on characters (region, realm, lower(name));

create table character_specs (
    character_id uuid         not null references characters (id) on delete cascade,
    spec         varchar(255) not null,
    primary key (character_id, spec)
);

create table character_standings (
    character_id uuid                     not null references characters (id) on delete cascade,
    bracket      varchar(255)             not null,
    rating       integer                  not null,
    season_id    integer                  not null,
    observed_at  timestamp with time zone not null,
    primary key (character_id, bracket)
);

-- The board filters and sorts by rating inside a bracket; this index is what
-- makes SearchBoard possible without a sequential scan.
create index ix_character_standings_rating
    on character_standings (bracket, rating desc);

-- Players: the account that owns characters.
--
-- Until now the dev auth stub accepted ANY UUID as X-Player-Id, so
-- characters.owner_id pointed at a value that existed nowhere. This migration
-- turns "an owner" from a claim into a row, and the foreign key below is what
-- keeps it true.

create table players (
    id         uuid        primary key,
    username   varchar(24) not null,
    battle_tag varchar(64) not null
);

-- The handle is a login identity, not a display name: two players asking for
-- "Emilio" and "emilio" want the same handle, so the index folds case and the
-- database, not the pre-check, is what makes it unique.
create unique index ux_players_username
    on players (lower(username));

-- One Battle.net account is one player. The value object already lowercases
-- the tag, so a plain index is enough.
create unique index ux_players_battle_tag
    on players (battle_tag);

-- The foreign key that makes the owner real.
--
-- NOT VALID on purpose, and it is not a shortcut: every character registered
-- before this migration has an owner id that no player holds, because there
-- was no players table to hold it. There is no honest way to backfill those
-- rows — inventing a username and a battle tag would be fabricating identity.
-- NOT VALID means the constraint is enforced for every NEW write (the rule
-- starts now) while the historical rows stay untouched. Once a development
-- database has been reset, `alter table characters validate constraint
-- fk_characters_owner` promotes it to a fully validated constraint.
alter table characters
    add constraint fk_characters_owner
    foreign key (owner_id) references players (id) on delete cascade
    not valid;

-- Composite foreign keys need the referenced side indexed (the primary key
-- already is); the referencing side needs one for the cascade and for the
-- "my characters" read that will come.
create index ix_characters_owner
    on characters (owner_id);

-- join_requests.owner_id is denormalized (see V3) and names a player too.
-- Leaving it unenforced while characters.owner_id is enforced would be a
-- half-true schema: same rule, same NOT VALID history, same cascade.
alter table join_requests
    add constraint fk_join_requests_owner
    foreign key (owner_id) references players (id) on delete cascade
    not valid;

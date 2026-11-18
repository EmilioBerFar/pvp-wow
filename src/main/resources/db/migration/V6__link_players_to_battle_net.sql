-- V6: the player becomes a Battle.net account.
--
-- WU17 gave the player a self-chosen handle and a battle tag, and treated the
-- handle as a login identity. WU18 replaces that: the identity now comes from
-- Battle.net, so the handle had no job left and its unique index only made a
-- second way to reject a registration.
--
-- The new key is the account id the provider calls `sub`. It is stored next to
-- the battle tag, NOT instead of it: the tag is contact data the product still
-- needs, and it can change (Battle.net sells renames) while `sub` never does.
-- Keying the account on the tag would turn a rename into a new player.
--
-- The column is NOT NULL from the start. A player exists because somebody
-- logged in with Battle.net, so there is no such thing as a player without an
-- account: the application has no other door that creates one, and the
-- application has never been deployed, so there are no historical rows to
-- protect. A nullable column would be a shape the code can never reach.

alter table players
    add column battle_net_id text not null;

-- One Battle.net account is one player. The `sub` claim is stored verbatim:
-- OpenID Connect defines it as a string, so a numeric column would be a
-- promise the provider never made.
create unique index ux_players_battle_net_id
    on players (battle_net_id);

-- The handle is gone, and its index with it: an index is a rule, and there is
-- no rule left to keep once the column it guarded no longer exists.
drop index ux_players_username;

alter table players
    drop column username;

-- The battle tag stops being unique too. Blizzard reclaims battle tags and
-- two accounts can carry the same one, so the old index would have rejected a
-- perfectly legitimate pair of players. The tag is contact data that the
-- board shows, never an identity: the account id is the identity. A plain
-- index keeps it searchable.
drop index ux_players_battle_tag;

create index ix_players_battle_tag
    on players (battle_tag);

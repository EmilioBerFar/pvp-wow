-- Join requests: the handshake between a player who wants to play and the
-- owner of a listing.
--
-- owner_id is DENORMALIZED on purpose. Two rules need the PLAYER, not the
-- character: a player cannot request the same listing with two characters,
-- and a player cannot keep more than a handful of requests open. The
-- character id is kept too, because a request belongs to a character and the
-- metrics are per character.
--
-- The request lives exactly as long as the listing: expires_at is COPIED
-- from the listing when the request is created. There is no separate TTL and
-- no separate configuration — a request to join a listing that dies in three
-- hours dies with it.

create table join_requests (
    id           uuid                     primary key,
    requester_id uuid                     not null references characters (id) on delete cascade,
    owner_id     uuid                     not null,
    listing_id   uuid                     not null references lfg_listings (id) on delete cascade,
    status       varchar(255)             not null,
    created_at   timestamp with time zone not null,
    expires_at   timestamp with time zone not null
);

-- One pending request per (player, listing): a player cannot ask to join the
-- same listing twice, not even with another character. The partial predicate
-- keeps answered requests out of the hot part of the index.
create unique index ux_join_requests_pending_per_player_listing
    on join_requests (owner_id, listing_id)
    where status = 'PENDING';

-- The pending requests of a player, for the "how many are you holding" rule
-- and for the sweep that cancels them all when one is accepted.
create index ix_join_requests_pending_per_player
    on join_requests (owner_id)
    where status = 'PENDING';

-- The facts of the listings that will never be visible again.
--
-- This table is what the metrics come from: the listings themselves are
-- deleted after the retention window, these facts are kept. character_id is
-- nullable ON DELETE SET NULL, so deleting a character does NOT delete the
-- history of what they published.
--
-- listing_id has NO foreign key on purpose: the fact must outlive the
-- listing it describes, exactly like it outlives the character. A cascade
-- here would delete the history the moment the retention job removed the
-- listing it was written for.
create table listing_outcomes (
    listing_id   uuid                     primary key,
    character_id uuid                     references characters (id) on delete set null,
    bracket      varchar(255)             not null,
    spec         varchar(255)             not null,
    kind         varchar(255)             not null,
    created_at   timestamp with time zone not null,
    closed_at    timestamp with time zone not null
);

-- The metrics read the facts by kind and by time.
create index ix_listing_outcomes_kind
    on listing_outcomes (kind, closed_at);

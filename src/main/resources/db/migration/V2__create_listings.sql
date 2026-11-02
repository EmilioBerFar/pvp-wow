-- LFG listings: the aggregate root plus languages and weekly availability.
--
-- The partial unique index is the final law for "one active listing per
-- character and bracket". PostgreSQL cannot put now() in an index predicate,
-- so the write adapter first marks due ACTIVE rows EXPIRED in the same
-- transaction, then inserts. The partial index still stops concurrent
-- publishers that both raced past the application-level pre-check.

create table lfg_listings (
    id                uuid                     primary key,
    character_id      uuid                     not null references characters (id) on delete cascade,
    bracket           varchar(255)             not null,
    spec              varchar(255)             not null,
    status            varchar(255)             not null,
    availability_zone varchar(255)             not null,
    created_at        timestamp with time zone not null,
    expires_at        timestamp with time zone not null,
    constraint ck_lfg_listings_expires_after_created check (expires_at > created_at)
);

create unique index ux_lfg_listings_active
    on lfg_listings (character_id, bracket)
    where status = 'ACTIVE';

-- The board reads ACTIVE rows for one bracket ordered newest first. Keeping
-- the predicate partial means cancelled/expired history does not sit in the
-- hot part of the index.
create index ix_lfg_listings_board
    on lfg_listings (bracket, created_at desc, id desc)
    where status = 'ACTIVE';

create table listing_languages (
    listing_id uuid        not null references lfg_listings (id) on delete cascade,
    language   varchar(2)  not null,
    primary key (listing_id, language)
);

create table listing_windows (
    listing_id  uuid        not null references lfg_listings (id) on delete cascade,
    day_of_week varchar(9)  not null,
    start_time  time        not null,
    end_time    time        not null,
    primary key (listing_id, day_of_week, start_time, end_time),
    constraint ck_listing_windows_start_before_end check (start_time < end_time)
);

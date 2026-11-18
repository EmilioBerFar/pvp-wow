-- V7: the session stops living in the heap.
--
-- WU18 made the session the credential: whoever holds the cookie IS the
-- player. While the session lived in memory, a restart logged everybody out
-- and a second instance would not have seen the sessions of the first. The
-- product is a single-instance deployment today, but the session is the one
-- thing that must survive a deploy: a rolling restart is a normal thing to
-- do to a server, and it should not log out the whole board.
--
-- The schema is the one Spring Session ships for PostgreSQL, copied here
-- instead of letting the application run it. The rule in this project is
-- that Flyway owns the schema: no table is ever created by the application
-- at boot, because a table the application can create is a table two
-- versions of the application can disagree about.
--
-- `spring.session.jdbc.initialize-schema: never` in application.yml is what
-- keeps that promise from the other side.

create table spring_session (
    primary_id             char(36)     not null,
    session_id             char(36)     not null,
    creation_time          bigint       not null,
    last_access_time       bigint       not null,
    max_inactive_interval  int          not null,
    expiry_time            bigint       not null,
    principal_name         varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

-- One session id, one row: the lookup on every request.
create unique index spring_session_ix1
    on spring_session (session_id);

-- The expired-session cleanup sweeps by expiry time, so that index is the
-- one the sweeper walks.
create index spring_session_ix2
    on spring_session (expiry_time);

-- Finding the sessions of one principal.
create index spring_session_ix3
    on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id  char(36)     not null,
    attribute_name      varchar(200) not null,
    attribute_bytes     bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    -- The attributes die with the session. Without the cascade, a deleted
    -- session would leave its attributes behind forever.
    constraint spring_session_attributes_fk
        foreign key (session_primary_id)
        references spring_session (primary_id)
        on delete cascade
);

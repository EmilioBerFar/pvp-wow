-- V8: the Battle.net token stops living in the heap of one process.
--
-- WU18 put the session in PostgreSQL so a deploy would not log everybody out.
-- The ACCESS TOKEN was left behind: Spring Security's default
-- OAuth2AuthorizedClientService is InMemoryOAuth2AuthorizedClientService, so
-- the token Battle.net gave us died with the JVM. The moment the product
-- needs that token for something - the WU19 import calls the Blizzard profile
-- API with it - a restart turns it into a 401 nobody can explain: the login
-- was days ago and the token evaporated with the process.
--
-- This table is where the authorized client lives. The columns are the ones
-- JdbcOAuth2AuthorizedClientService reads and writes - and they are worth
-- reading carefully, because what is NOT here is the point:
--
--   There is no ClientRegistration column. The service stores the
--   registration ID (a name, 'battle-net'), not the registration itself. The
--   client SECRET lives in the ClientRegistration bean, in memory, and it is
--   never written here. That is the whole reason this table exists instead of
--   the session: the session stores serialized objects, and a serialized
--   OAuth2AuthorizedClient carries the ClientRegistration - and with it the
--   secret - inside it.
--
-- The schema is the one Spring Security documents for this service, copied
-- here and adapted to PostgreSQL, exactly like V7 did with the session
-- tables. The rule in this project is that Flyway owns the schema: a table
-- the application can create at boot is a table two versions of the
-- application can disagree about.
--
-- The access token IS a credential and it IS stored here, in the clear. That
-- is the honest trade: the token is a bearer credential scoped to the
-- 'wow.profile' scope, and the database that holds it is already the one that
-- holds the sessions that authenticate the player. What the token buys is the
-- ability to call Blizzard on the player's behalf; what it does NOT buy is
-- the ability to log in as the player - that is the session, and the session
-- is not readable from here by anybody who does not already have the
-- database.

create table oauth2_authorized_client (
    client_registration_id varchar(100) not null,
    principal_name         varchar(200) not null,
    access_token_type      varchar(100) not null,
    -- The token itself. 'blob' in the reference DDL; PostgreSQL's bytea.
    access_token_value     bytea        not null,
    access_token_issued_at timestamp    not null,
    access_token_expires_at timestamp   not null,
    access_token_scopes    varchar(1000),
    refresh_token_value    bytea,
    refresh_token_issued_at timestamp,
    created_at             timestamp    not null default current_timestamp,
    constraint oauth2_authorized_client_pk primary key (client_registration_id, principal_name)
);

-- The expiry is what the service checks when it loads a client, and the
-- cleanup of an expired row is a delete by expiry time. This index is what
-- that sweep walks.
create index ix_oauth2_authorized_client_expires_at
    on oauth2_authorized_client (access_token_expires_at);

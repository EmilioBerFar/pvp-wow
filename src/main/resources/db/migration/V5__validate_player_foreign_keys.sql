-- V5: promote the two player foreign keys from NOT VALID to fully validated.
--
-- WU17 added them NOT VALID (V4) because every character registered through
-- the old UUID stub has an owner id that no player holds — there was no
-- players table to hold it — and backfilling would mean inventing a username
-- and a battle tag, which is fabricating identity. NOT VALID enforces the rule
-- for every NEW write while leaving the historical rows alone.
--
-- This is the second step of the technique, and the split is deliberate:
--
--   * ADD CONSTRAINT ... NOT VALID takes a SHARE ROW EXCLUSIVE lock and does
--     NOT scan the table.
--   * VALIDATE CONSTRAINT takes a SHARE UPDATE EXCLUSIVE lock — reads and
--     writes keep flowing — and scans the table once.
--
-- Doing both in one ALTER would hold the stronger lock for the whole scan,
-- which is the classic way to freeze a large table. Here the table is tiny and
-- there is no production data, so the split costs nothing; the discipline is
-- what we keep. The constraint also becomes fully trusted for the planner.
--
-- If a local database still has orphans, this migration FAILS, loudly, on
-- purpose: a constraint that lies is worse than a failed boot. Clean the data
-- first — see the WU17 section of docs/domain-spec.md.

alter table characters
    validate constraint fk_characters_owner;

alter table join_requests
    validate constraint fk_join_requests_owner;

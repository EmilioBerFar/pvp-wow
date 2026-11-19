-- V9: the character learns the name Blizzard knows it by.
--
-- Every character so far was identified by (region, realm, name) - the
-- natural key of the game. That key is NOT stable: paid renames exist, and
-- realms move. A character that changes its name is still the SAME
-- character, and the only thing that says so is the id the provider gave it.
-- It is also what makes a re-import recognise a character instead of
-- duplicating it: the import asks "is this the character Blizzard calls
-- 161996209?", and the answer does not depend on what it is called today.
--
-- The column is NOT NULL, and that is not laziness: every character in this
-- product comes from the import. A character registered by hand - with a
-- rating nobody verified, on a board whose whole value is that its ratings
-- can be trusted - is not a character this product has. So there is no such
-- thing as a character without a Blizzard id.
--
-- WHY NOT NULL IS ADDED IN TWO STEPS: PostgreSQL refuses to add a NOT NULL
-- column to a table that has rows, and a developer's database may have some.
-- The same technique V5 used for the foreign keys:
--
--   * ADD CONSTRAINT ... NOT VALID takes a SHARE ROW EXCLUSIVE lock and does
--     NOT scan the table.
--   * VALIDATE CONSTRAINT takes a SHARE UPDATE EXCLUSIVE lock - reads and
--     writes keep flowing - and scans the table once.
--
-- Doing both in one statement would hold the stronger lock for the whole
-- scan, which is the classic way to freeze a large table. Here the table is
-- tiny and there is no production data, so the split costs nothing; the
-- discipline is what we keep. The constraint also becomes fully trusted for
-- the planner.
--
-- The id is NOT part of the natural key, and the two indexes say so:
--
--   * ux_characters_blizzard_character_id is the law of the import: one
--     Blizzard character, one row. It is (region, id) and not (id) alone
--     because the provider numbers its ids per region - the character 42 of
--     the EU realms and the character 42 of the US realms are different
--     characters, and an index on the id alone would reject the second one.
--
--   * ux_characters_natural_key (from V1) stays: it is the rule that two
--     players cannot own the same 'Thrall' on the same realm. It is the
--     game's own rule - a character belongs to one account - and it does not
--     become less true because the import now recognises characters by id.

alter table characters
    add column blizzard_character_id bigint;

-- The law of the import: one Blizzard character, one row, per region.
create unique index ux_characters_blizzard_character_id
    on characters (region, blizzard_character_id);

-- And now the column cannot be empty. The NOT VALID / VALIDATE split keeps
-- the table readable while the constraint is checked.
alter table characters
    add constraint ck_characters_blizzard_character_id_not_null
    check (blizzard_character_id is not null) not valid;

alter table characters
    validate constraint ck_characters_blizzard_character_id_not_null;

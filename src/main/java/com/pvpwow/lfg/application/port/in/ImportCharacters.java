package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.character.Region;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Driving port: the characters of the signed-in player's Battle.net account,
 * and the player's choice of which ones to import.
 *
 * <p>TWO OPERATIONS, TWO COSTS: listing the account is ONE call to Blizzard
 * and touches nothing in our database. Importing is the expensive one, and
 * the player says when it happens - so the cost is spent on characters the
 * player actually wants, not on the whole account.
 *
 * <p>WHY THE IMPORT DOES NOT FAIL AS A WHOLE: an import is a batch, and one
 * character in a batch can be refused for reasons that say nothing about the
 * others - a specialization we do not know, a character that belongs to
 * another player, a character the domain will not accept. A batch that dies
 * because of one member is a batch that throws away work the player asked
 * for. So the result says what happened to EACH character.
 */
public interface ImportCharacters {

    /**
     * The characters the player could import: the whole account, in one
     * region.
     *
     * <p>One call to Blizzard. Nothing is written.
     */
    List<ImportableCharacter> listImportable(Region region);

    /**
     * Imports the chosen characters.
     *
     * <p>The result says what happened to each one. Nothing here throws
     * because of a single character.
     */
    ImportReport importChosen(Region region, List<ChosenCharacter> chosen);

    /**
     * One character of the account, as the listing reports it.
     */
    record ImportableCharacter(
            long blizzardId,
            String name,
            String realmSlug,
            String className,
            int level) {

        public ImportableCharacter {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(realmSlug, "realmSlug must not be null");
            Objects.requireNonNull(className, "className must not be null");
        }
    }

    /**
     * The character the player chose to import: ITS ID, AND NOTHING ELSE.
     *
     * <p>WHY THE NAME AND THE REALM DO NOT TRAVEL: the character endpoint of
     * the Blizzard API is PUBLIC. It answers for any character in the game,
     * whoever owns it. So a client that sent a name and a realm could ask
     * the server to import somebody else's character - and the only thing
     * that stopped it was the character already being in our database. That
     * is not a guard, it is a race: the first one in would take it.
     *
     * <p>So the client sends an id and the server checks it against the
     * ACCOUNT LIST - the one endpoint that answers with the token of the
     * signed-in player, and therefore with THEIR characters. The name and
     * the realm come from that list, never from the client. An id that is
     * not in the account is not importable, and the server says so without
     * saying anything about who does own it.
     */
    record ChosenCharacter(long blizzardId) {

        public ChosenCharacter {
            if (blizzardId <= 0) {
                throw new IllegalArgumentException(
                        "blizzardId must be positive, got: " + blizzardId);
            }
        }
    }

    /**
     * What happened to one character.
     */
    record CharacterOutcome(
            String name,
            String realmSlug,
            Outcome outcome,
            String reason) {

        public CharacterOutcome {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(realmSlug, "realmSlug must not be null");
            Objects.requireNonNull(outcome, "outcome must not be null");
        }

        public static CharacterOutcome imported(String name, String realmSlug) {
            return new CharacterOutcome(name, realmSlug, Outcome.IMPORTED, null);
        }

        public static CharacterOutcome refreshed(String name, String realmSlug) {
            return new CharacterOutcome(name, realmSlug, Outcome.REFRESHED, null);
        }

        public static CharacterOutcome skipped(String name, String realmSlug, String reason) {
            return new CharacterOutcome(name, realmSlug, Outcome.SKIPPED, reason);
        }

        public static CharacterOutcome rejected(String name, String realmSlug, String reason) {
            return new CharacterOutcome(name, realmSlug, Outcome.REJECTED, reason);
        }
    }

    /**
     * The four things that can happen to a character.
     *
     * <p>IMPORTED and REFRESHED are the two successes, and they are not the
     * same: the first created the character, the second found it and updated
     * its observed facts. SKIPPED means there was nothing to do - the
     * character already exists and belongs to somebody else. REJECTED means
     * the character was refused, and it carries the reason.
     */
    enum Outcome {
        IMPORTED,
        REFRESHED,
        SKIPPED,
        REJECTED
    }

    /**
     * The result of an import: one outcome per character chosen.
     */
    record ImportReport(List<CharacterOutcome> outcomes) {

        public ImportReport {
            Objects.requireNonNull(outcomes, "outcomes must not be null");
            outcomes = List.copyOf(outcomes);
        }

        /**
         * The characters that were created.
         */
        public long imported() {
            return count(Outcome.IMPORTED);
        }

        /**
         * The characters whose observed facts were refreshed.
         */
        public long refreshed() {
            return count(Outcome.REFRESHED);
        }

        /**
         * The characters that were left alone.
         */
        public long skipped() {
            return count(Outcome.SKIPPED);
        }

        /**
         * The characters the domain refused, and why.
         */
        public long rejected() {
            return count(Outcome.REJECTED);
        }

        private long count(Outcome outcome) {
            return outcomes.stream().filter(o -> o.outcome() == outcome).count();
        }
    }
}

package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Region;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Driven port: the World of Warcraft characters of the signed-in player, as
 * Blizzard reports them.
 *
 * <p>WHY THE PORT SPEAKS RAW: the declared/observed seam. The facts Blizzard
 * reports are OBSERVED - the class, the item level, the ratings - and they
 * arrive as data, not as domain objects, because the domain's value objects
 * validate what the DOMAIN believes is possible. A rating of 5000 or a
 * specialization nobody has heard of is not an error in the upstream: it is
 * information the use case has to decide what to do with. Translating here
 * would put a translation decision in the adapter, and the adapter does not
 * get to decide it.
 *
 * <p>So the port reports what it saw, in the smallest vocabulary that can
 * carry it, and the use case decides. The adapter's job is to be a faithful
 * witness: to say "Blizzard said the character is a Mage with Frost, at item
 * level 702, and rated 1800 in 2v2 for season 43".
 *
 * <p>WHY THE CALLS ARE SPLIT IN TWO: the cost of talking to Blizzard is not
 * flat. The list of the account is ONE call and tells the player what they
 * could import; the details of a character are THREE more (the profile, the
 * PvP summary, and one per bracket the summary says exists). Asking for the
 * details of every character to show a list would multiply the cost by the
 * size of the account - and most players never import most of their toons.
 * The port therefore has one method for the list and one for the details of
 * a character the player chose.
 */
public interface BattleNetCharacters {

    /**
     * The characters of the signed-in player's account in one region.
     *
     * <p>One call. The player chooses from this list, and only the chosen
     * ones cost more.
     *
     * <p>ONE REGION AT A TIME, and that is not a limitation of this port but
     * of the world: a Battle.net account can hold several World of Warcraft
     * accounts, one per region, and each region has its own namespace. The
     * characters reported here all live in {@code region}.
     */
    List<AccountCharacter> charactersIn(Region region);

    /**
     * The observed facts of one character: its class, its gear, and its
     * arena standing.
     *
     * <p>Empty when the character cannot be read - a character deleted, a
     * name changed, a realm moved. Empty is the honest answer: "there is
     * nothing there", not "something went wrong".
     */
    Optional<CharacterFacts> factsOf(CharacterReference character);

    /**
     * One character as the account listing reports it: enough to show it and
     * to ask for its details later.
     */
    record AccountCharacter(
            long blizzardId,
            String name,
            String realmSlug,
            String className,
            int level) {

        public AccountCharacter {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(realmSlug, "realmSlug must not be null");
            Objects.requireNonNull(className, "className must not be null");
        }
    }

    /**
     * The character to ask the details of. It carries the Blizzard id
     * because that is the identity that survives a rename: a name and a realm
     * can change (paid renames exist, and realms move), the id cannot.
     */
    record CharacterReference(
            long blizzardId,
            String name,
            String realmSlug,
            Region region) {

        public CharacterReference {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(realmSlug, "realmSlug must not be null");
            Objects.requireNonNull(region, "region must not be null");
        }
    }

    /**
     * What Blizzard reports about one character.
     *
     * <p>The ratings are a map because the upstream decides which brackets
     * exist: a character who never played 3v3 has no 3v3 entry, and that is
     * the same fact our domain calls "unranked". A bracket the character did
     * not play is ABSENT, not zero.
     *
     * <p>The specialization is the ONE fact here that can be absent, and the
     * upstream is the one that leaves it out: a character below level 10 has
     * no specialization yet, and the API omits the field. The domain will
     * refuse that character - a character must declare at least one
     * specialization - but the DECISION belongs to the domain, not to this
     * port. A port that dropped the character would be deciding on the
     * upstream's behalf.
     */
    record CharacterFacts(
            String className,
            String specializationName,
            int equippedItemLevel,
            java.util.Map<Bracket, Rating> ratings) {

        public CharacterFacts {
            Objects.requireNonNull(className, "className must not be null");
            ratings = Map.copyOf(ratings);
        }

        /**
         * Whether the upstream reported a specialization at all.
         */
        public boolean hasSpecialization() {
            return specializationName != null && !specializationName.isBlank();
        }
    }

    /**
     * A rating as the upstream reports it: the number, and the season it
     * belongs to.
     *
     * <p>The season travels WITH the rating because a rating without a season
     * is meaningless - ratings reset every season, and "1800" in season 3 and
     * "1800" in season 4 are two different facts. The upstream reports
     * {@code season.id} alongside the rating, so nothing has to be guessed.
     */
    record Rating(int value, PvpSeason season) {

        public Rating {
            Objects.requireNonNull(season, "season must not be null");
        }
    }
}

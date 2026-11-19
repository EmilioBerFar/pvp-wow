package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.ImportCharacters;
import com.pvpwow.lfg.application.port.in.ImportCharacters.CharacterOutcome;
import com.pvpwow.lfg.application.port.in.ImportCharacters.ChosenCharacter;
import com.pvpwow.lfg.application.port.in.ImportCharacters.ImportReport;
import com.pvpwow.lfg.application.port.in.ImportCharacters.ImportableCharacter;
import com.pvpwow.lfg.domain.character.Region;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The import: the characters of the signed-in player's Battle.net account, and
 * the ones they choose to bring in.
 *
 * <p>TWO ENDPOINTS, TWO COSTS. The listing is ONE call to Blizzard and writes
 * nothing. The import is the expensive one, and the player says when it
 * happens - so the cost is spent on characters the player actually wants, not
 * on the whole account.
 *
 * <p>THE REGION IS A PARAMETER because a Battle.net account can hold several
 * World of Warcraft accounts, one per region, and each is served by its own
 * namespace. A player with an EU account and a US account imports them with
 * two calls, and each character lands with the region of the realm it is
 * really on.
 */
@RestController
@RequestMapping("/api/characters")
public final class ImportCharactersController {

    private final ImportCharacters importCharacters;

    public ImportCharactersController(ImportCharacters importCharacters) {
        this.importCharacters = importCharacters;
    }

    @GetMapping("/importable")
    List<ImportableCharacterResponse> importable(@RequestParam String region) {
        List<ImportableCharacter> characters =
                importCharacters.listImportable(WireParsing.parseEnum(Region.class, region));
        return characters.stream().map(ImportableCharacterResponse::from).toList();
    }

    @PostMapping("/import")
    ImportReportResponse importChosen(@RequestParam String region,
                                       @Valid @RequestBody ImportCharactersRequest request) {
        ImportReport report = importCharacters.importChosen(
                WireParsing.parseEnum(Region.class, region),
                request.toChoices());
        return ImportReportResponse.from(report);
    }

    /**
     * One character of the account, as the listing reports it.
     */
    record ImportableCharacterResponse(
            long blizzardId,
            String name,
            String realmSlug,
            String className,
            int level) {

        static ImportableCharacterResponse from(ImportableCharacter character) {
            return new ImportableCharacterResponse(
                    character.blizzardId(),
                    character.name(),
                    character.realmSlug(),
                    character.className(),
                    character.level());
        }
    }

    /**
     * What the player chose.
     */
    record ImportCharactersRequest(@NotNull List<@Valid ChosenCharacterRequest> characters) {

        ImportCharactersRequest {
            characters = characters == null ? List.of() : List.copyOf(characters);
        }

        List<ChosenCharacter> toChoices() {
            return characters.stream().map(ChosenCharacterRequest::toChoice).toList();
        }
    }

    /**
     * One chosen character: ITS ID, AND NOTHING ELSE.
     *
     * <p>The name and the realm are deliberately NOT accepted. The character
     * endpoint of the Blizzard API is public - it answers for any character
     * in the game - so a client that could name a character could ask the
     * server to import somebody else's. The server checks the id against the
     * account of the signed-in player, and takes the name and the realm from
     * there.
     */
    record ChosenCharacterRequest(@NotNull Long blizzardId) {

        ChosenCharacter toChoice() {
            return new ChosenCharacter(blizzardId);
        }
    }

    /**
     * The result of an import: one outcome per character chosen.
     */
    record ImportReportResponse(
            long imported,
            long refreshed,
            long skipped,
            long rejected,
            List<CharacterOutcomeResponse> outcomes) {

        static ImportReportResponse from(ImportReport report) {
            return new ImportReportResponse(
                    report.imported(),
                    report.refreshed(),
                    report.skipped(),
                    report.rejected(),
                    report.outcomes().stream().map(CharacterOutcomeResponse::from).toList());
        }
    }

    /**
     * What happened to one character.
     */
    record CharacterOutcomeResponse(
            String name,
            String realmSlug,
            String outcome,
            String reason) {

        static CharacterOutcomeResponse from(CharacterOutcome outcome) {
            return new CharacterOutcomeResponse(
                    outcome.name(),
                    outcome.realmSlug(),
                    outcome.outcome().name(),
                    outcome.reason());
        }
    }
}

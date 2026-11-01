package com.pvpwow.lfg.adapter.armory;

import com.pvpwow.lfg.domain.character.Region;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmoryLinksTest {

    @Test
    void buildsTheExactUrlOfARealLiveCharacter() {
        // Copied 2026-11-01 from the live armory: a real EU character on Dun
        // Modr. This test is the tripwire — if Blizzard reshapes the URL
        // family (new expansion, new game-version segment), this is what
        // fails, and GAME_VERSION plus the expected shape are what change.
        assertThat(ArmoryLinks.url(Region.EU, "es-es", "dun-modr", "strongem"))
                .isEqualTo("https://worldofwarcraft.blizzard.com/es-es/worldsoul/eu/armory/character/dun-modr/strongem");
    }

    @Test
    void buildsASecondLiveShapeToPinTheUsRegion() {
        // Second real URL from the live armory, different region and locale.
        assertThat(ArmoryLinks.url(Region.US, "en-us", "illidan", "tofuru"))
                .isEqualTo("https://worldofwarcraft.blizzard.com/en-us/worldsoul/us/armory/character/illidan/tofuru");
    }

    @Test
    void theLocaleIsTheCallersChoiceNotDerivedFromTheRegion() {
        assertThat(ArmoryLinks.url(Region.EU, "fr-fr", "dun-modr", "strongem"))
                .contains("/fr-fr/worldsoul/eu/");
    }

    @Test
    void lowercasesTheNameLikeTheLiveArmoryDoes() {
        // In-game names are capitalized ("Strongem"); every live armory URL
        // carries them lowercase ("strongem").
        assertThat(ArmoryLinks.url(Region.EU, "es-es", "dun-modr", "Strongem"))
                .endsWith("/armory/character/dun-modr/strongem");
    }

    @Test
    void percentEncodesAccentedNamesAfterLowercasing() {
        assertThat(ArmoryLinks.url(Region.EU, "es-es", "sanguino", "Ñandú"))
                .endsWith("/armory/character/sanguino/%C3%B1and%C3%BA");
    }

    @Test
    void encodesSpacesAsPercent20NeverAsAPlus() {
        assertThat(ArmoryLinks.url(Region.EU, "es-es", "los-errantes", "Frost Bolt"))
                .endsWith("/frost%20bolt");
    }

    @Test
    void rejectsBlankInputs() {
        assertThatThrownBy(() -> ArmoryLinks.url(Region.EU, " ", "dun-modr", "strongem"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArmoryLinks.url(Region.EU, "es-es", "", "strongem"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArmoryLinks.url(Region.EU, "es-es", "dun-modr", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> ArmoryLinks.url(null, "es-es", "dun-modr", "strongem"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ArmoryLinks.url(Region.EU, null, "dun-modr", "strongem"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ArmoryLinks.url(Region.EU, "es-es", null, "strongem"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ArmoryLinks.url(Region.EU, "es-es", "dun-modr", null))
                .isInstanceOf(NullPointerException.class);
    }
}

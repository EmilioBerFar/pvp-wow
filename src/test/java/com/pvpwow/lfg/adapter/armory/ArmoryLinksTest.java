package com.pvpwow.lfg.adapter.armory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmoryLinksTest {

    @Test
    void buildsTheUrlFromTheCallersLocaleAndCanonicalInputs() {
        assertThat(ArmoryLinks.url("es-es", "sanguino", "Frostbolt"))
                .isEqualTo("https://worldofwarcraft.blizzard.com/es-es/character/sanguino/Frostbolt");
    }

    @Test
    void theLocaleIsTheCallersChoiceNotDerivedFromTheRegion() {
        assertThat(ArmoryLinks.url("fr-fr", "sanguino", "Frostbolt"))
                .contains("/fr-fr/");
    }

    @Test
    void percentEncodesAccentedNamesThatUsedToProduceABrokenUrl() {
        assertThat(ArmoryLinks.url("es-es", "sanguino", "Ñandú"))
                .isEqualTo("https://worldofwarcraft.blizzard.com/es-es/character/sanguino/%C3%91and%C3%BA");
    }

    @Test
    void encodesSpacesAsPercent20NeverAsAPlus() {
        assertThat(ArmoryLinks.url("es-es", "los-errantes", "Frost Bolt"))
                .endsWith("/Frost%20Bolt");
    }

    @Test
    void rejectsBlankInputs() {
        assertThatThrownBy(() -> ArmoryLinks.url(" ", "sanguino", "Frostbolt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArmoryLinks.url("es-es", "", "Frostbolt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArmoryLinks.url("es-es", "sanguino", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> ArmoryLinks.url(null, "sanguino", "Frostbolt"))
                .isInstanceOf(NullPointerException.class);
    }
}

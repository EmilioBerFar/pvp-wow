package com.pvpwow.lfg.adapters.in.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client configuration check, on its own.
 *
 * <p>The interesting case is the unresolved placeholder: Spring leaves
 * {@code ${BATTLE_NET_CLIENT_ID}} in the value when the variable is absent,
 * so a naive "is it empty?" check would wave it through, and the
 * application would start with a literal placeholder as its client id.
 */
class BattleNetClientConfigurationTest {

    @Test
    void aRealClientIsAccepted() {
        assertThatCode(() -> configuration("real-id", "real-secret"))
                .doesNotThrowAnyException();
    }

    @Test
    void aMissingClientIdIsRefused() {
        assertThatThrownBy(() -> configuration(null, "real-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BATTLE_NET_CLIENT_ID");
    }

    @Test
    void anEmptyClientIdIsRefused() {
        assertThatThrownBy(() -> configuration("  ", "real-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BATTLE_NET_CLIENT_ID");
    }

    @Test
    void anUnresolvedPlaceholderIsRefused() {
        assertThatThrownBy(() -> configuration("${BATTLE_NET_CLIENT_ID}", "real-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BATTLE_NET_CLIENT_ID")
                .hasMessageContaining("unresolved placeholder");
    }

    @Test
    void aMissingClientSecretIsRefused() {
        assertThatThrownBy(() -> configuration("real-id", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BATTLE_NET_CLIENT_SECRET");
    }

    @Test
    void anUnresolvedPlaceholderSecretIsRefused() {
        assertThatThrownBy(() -> configuration("real-id", "${BATTLE_NET_CLIENT_SECRET}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BATTLE_NET_CLIENT_SECRET");
    }

    private void configuration(String clientId, String clientSecret) {
        new BattleNetClientConfiguration()
                .battleNetClientIsConfigured(clientId, clientSecret);
    }
}

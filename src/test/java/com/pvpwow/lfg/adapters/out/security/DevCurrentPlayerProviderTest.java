package com.pvpwow.lfg.adapters.out.security;

import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevCurrentPlayerProviderTest {

    private final DevCurrentPlayerProvider provider = new DevCurrentPlayerProvider();

    @AfterEach
    void cleanUpRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesThePlayerFromTheHeader() {
        UUID id = UUID.randomUUID();
        requestWithHeader("X-Player-Id", id.toString());

        assertThat(provider.currentPlayer()).isEqualTo(new PlayerId(id));
    }

    @Test
    void aMissingHeaderIsUnauthorized() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("X-Player-Id");
    }

    @Test
    void aGarbageUuidIsUnauthorized() {
        requestWithHeader("X-Player-Id", "not-a-uuid");

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void noRequestContextAtAllIsUnauthorized() {
        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("request context");
    }

    private void requestWithHeader(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(name, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}

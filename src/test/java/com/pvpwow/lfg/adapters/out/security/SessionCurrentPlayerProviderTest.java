package com.pvpwow.lfg.adapters.out.security;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real adapter, without a servlet container: a session attribute is the
 * whole contract, and every way it can be missing is a 401.
 */
class SessionCurrentPlayerProviderTest {

    private final SessionCurrentPlayerProvider provider = new SessionCurrentPlayerProvider();

    @AfterEach
    void unbindTheRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsThePlayerIdTheLoginHandlerWrote() {
        PlayerId expected = new PlayerId(UUID.randomUUID());
        bind(new MockHttpServletRequest(), expected);

        assertThat(provider.currentPlayer()).isEqualTo(expected);
    }

    @Test
    void aSessionWithoutTheAttributeIsA401() {
        bind(new MockHttpServletRequest(), null);

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Not signed in");
    }

    @Test
    void noRequestAtAllIsA401WithItsOwnMessage() {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("No HTTP request context");
    }

    private void bind(MockHttpServletRequest request, PlayerId id) {
        if (id != null) {
            request.getSession(true).setAttribute(
                    SessionCurrentPlayerProvider.PLAYER_ID_ATTRIBUTE, id.value());
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}

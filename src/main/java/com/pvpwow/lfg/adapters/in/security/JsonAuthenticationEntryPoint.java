package com.pvpwow.lfg.adapters.in.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What an unauthenticated request gets: a 401 ProblemDetail, never a redirect.
 *
 * <p>Spring's OAuth2 login support defaults to redirecting the browser to the
 * provider. That is right for a website and wrong for an API: a fetch() that
 * follows the redirect gets an HTML login page instead of a status it can
 * branch on. The redirect still exists - it is just explicit, at
 * {@code /oauth2/authorization/battle-net}, chosen by the client.
 *
 * <p>The body is written by hand rather than serialised from {@code
 * ProblemDetail}: this runs in the filter chain, before any MVC message
 * converter or advice is involved, and four stable fields are cheaper than
 * teaching a second layer about problem+json.
 */
final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper json;

    JsonAuthenticationEntryPoint(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", "Unauthorized");
        problem.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        problem.put("detail",
                "Not signed in: start the login at GET /oauth2/authorization/battle-net");
        json.writeValue(response.getOutputStream(), problem);
    }
}

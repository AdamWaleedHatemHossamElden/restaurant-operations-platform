package com.adam.restaurantoperations.auth.security;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

public final class AuthenticationPathHttpFirewall implements HttpFirewall {

    private static final Pattern MATRIX_PARAMETERS = Pattern.compile(";[^/]*");
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final StrictHttpFirewall defaultFirewall = new StrictHttpFirewall();
    private final StrictHttpFirewall cookieAuthenticationFirewall = cookieAuthenticationFirewall();

    @Override
    public FirewalledRequest getFirewalledRequest(HttpServletRequest request) {
        return permitsMatrixParameters(request)
                ? cookieAuthenticationFirewall.getFirewalledRequest(request)
                : defaultFirewall.getFirewalledRequest(request);
    }

    @Override
    public HttpServletResponse getFirewalledResponse(HttpServletResponse response) {
        return defaultFirewall.getFirewalledResponse(response);
    }

    private boolean permitsMatrixParameters(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String pathWithoutMatrixParameters = MATRIX_PARAMETERS.matcher(path).replaceAll("");
        return matchesEndpoint(pathWithoutMatrixParameters, REFRESH_PATH)
                || matchesEndpoint(pathWithoutMatrixParameters, LOGOUT_PATH);
    }

    private boolean matchesEndpoint(String path, String endpoint) {
        return endpoint.equals(path) || (endpoint + "/").equals(path);
    }

    private StrictHttpFirewall cookieAuthenticationFirewall() {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(true);
        return firewall;
    }
}

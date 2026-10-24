package com.msme.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

public final class BrowserAuthentication {
    private BrowserAuthentication() {}
    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) for (var cookie : request.getCookies())
            if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public static String authorization(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null) return bearer;
        String token = cookie(request, "MSME_AUTH");
        return token == null || token.isBlank() ? null : "Bearer " + token;
    }
    public static boolean csrfValid(HttpServletRequest request) {
        if (request.getHeader("Authorization") != null || cookie(request, "MSME_AUTH") == null
                || Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
        String expected = cookie(request, "MSME_CSRF"), supplied = request.getHeader("X-CSRF-Token");
        return expected != null && supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}

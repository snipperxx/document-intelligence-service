package io.github.snipperxx.docintel.auth;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The token subject is the numeric user id set by {@link TokenService}.
     */
    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}

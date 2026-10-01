package io.github.snipperxx.docintel.auth;

import io.github.snipperxx.docintel.common.ConflictException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AppUser register(String email, String displayName, String rawPassword) {
        String normalizedEmail = normalize(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }
        return users.save(new AppUser(normalizedEmail, displayName.strip(), passwordEncoder.encode(rawPassword)));
    }

    @Transactional(readOnly = true)
    public TokenService.IssuedToken login(String email, String rawPassword) {
        AppUser user = users.findByEmailIgnoreCase(normalize(email))
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                // Same error for unknown email and wrong password, so accounts cannot be enumerated.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return tokenService.issue(user);
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}

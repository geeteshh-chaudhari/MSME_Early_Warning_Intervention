
package com.msme.security;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    prefix = "msme.security.local-users",
    name = "enabled",
    havingValue = "true"
)
public class AssessmentUserDetailsService
        implements UserDetailsService {

    private final Map<String, UserDetails> users;

    public AssessmentUserDetailsService(
            PasswordEncoder encoder,
            @Value("${msme.security.admin.username}")
            String adminUsername,
            @Value("${msme.security.admin.password}")
            String adminPassword,
            @Value("${msme.security.rm.username}")
            String rmUsername,
            @Value("${msme.security.rm.password}")
            String rmPassword) {

        if (adminUsername.isBlank()
                || rmUsername.isBlank()
                || adminUsername.equals(rmUsername)) {
            throw new IllegalStateException(
                "Invalid local API usernames");
        }

        if (adminPassword.isBlank()
                || rmPassword.isBlank()) {
            throw new IllegalStateException(
                "Local API passwords must be configured");
        }

        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(encoder.encode(adminPassword))
                .roles("RISK_ADMIN")
                .build();

        UserDetails rm = User.builder()
                .username(rmUsername)
                .password(encoder.encode(rmPassword))
                .roles("RM")
                .build();

        this.users = Map.of(
                adminUsername, admin,
                rmUsername, rm
        );
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserDetails user = users.get(username);

        if (user == null) {
            throw new UsernameNotFoundException(
                "API user not found");
        }

        return new User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                user.isAccountNonExpired(),
                user.isCredentialsNonExpired(),
                user.isAccountNonLocked(),
                user.getAuthorities()
        );
    }
}

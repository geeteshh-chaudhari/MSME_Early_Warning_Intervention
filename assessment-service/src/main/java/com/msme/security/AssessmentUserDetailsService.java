package com.msme.security;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AssessmentUserDetailsService
        implements UserDetailsService {

    private static final Set<String> VALID_ROLES =
            Set.of("RISK_ADMIN", "RM", "MSME_REP");

    private final NamedParameterJdbcTemplate jdbc;
    private final String userTable;

    public AssessmentUserDetailsService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${msme.security.user-table:USER1.APP_USER}")
            String userTable) {

        if (userTable == null
                || !userTable.matches(
                    "[A-Za-z][A-Za-z0-9_]*"
                    + "(\\.[A-Za-z][A-Za-z0-9_]*)?"
                )) {

            throw new IllegalArgumentException(
                    "Invalid configured user table"
            );
        }

        this.jdbc = jdbc;
        this.userTable = userTable;
    }

    public CustomUserPrincipal loadUserById(Long userId) {

        if (userId == null || userId <= 0) {
            throw new UsernameNotFoundException(
                    "User not found"
            );
        }

        String sql = """
                SELECT USER_ID, ROLE, MSME_ID, ACCOUNT_STATUS
                FROM %s
                WHERE USER_ID = :userId
                """.formatted(userTable);

        List<CustomUserPrincipal> users = jdbc.query(
                sql,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> new CustomUserPrincipal(
                        rs.getLong("USER_ID"),
                        rs.getString("ROLE"),
                        rs.getObject("MSME_ID", Long.class),
                        "ACTIVE".equalsIgnoreCase(
                                rs.getString("ACCOUNT_STATUS")
                        )
                )
        );

        if (users.size() != 1) {
            throw new UsernameNotFoundException(
                    "User not found"
            );
        }

        CustomUserPrincipal principal = users.get(0);

        if (principal.getRole() == null
                || !VALID_ROLES.contains(principal.getRole())) {

            throw new UsernameNotFoundException(
                    "User has an invalid role"
            );
        }

        if ("MSME_REP".equals(principal.getRole())) {
            String owner = userTable.contains(".") ? userTable.substring(0, userTable.lastIndexOf('.') + 1) : "";
            Integer active = principal.getMsmeId() == null ? 0 : jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + owner + "MSME WHERE MSME_ID=:id AND IS_ACTIVE='Y'",
                    new MapSqlParameterSource("id", principal.getMsmeId()), Integer.class);
            if (active == null || active != 1) throw new UsernameNotFoundException("Business account is inactive");
        }
        return principal;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        throw new UsernameNotFoundException(
                "Use the JWT issued by MS1"
        );
    }
}
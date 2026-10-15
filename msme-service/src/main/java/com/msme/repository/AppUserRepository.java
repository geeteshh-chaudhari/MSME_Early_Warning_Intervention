package com.msme.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.msme.entity.AppUser;

@Repository
public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    /*
     * Used during login.
     */
    Optional<AppUser> findByEmail(String email);

    /*
     * Used before creating/updating a user
     * to avoid duplicate email addresses.
     */
    boolean existsByEmail(String email);

    /*
     * Because uq_app_user_msme guarantees that an MSME can have
     * at most one MSME representative.
     */
    Optional<AppUser> findByMsmeId(Long msmeId);

    /*
     * Used by Risk Admin to filter users by role.
     *
     * Examples:
     * RM
     * MSME_REP
     * RISK_ADMIN
     */
    List<AppUser> findByRole(String role);

    /*
     * Used to obtain MSME representatives belonging to a set
     * of MSMEs.
     *
     * The service first finds MSMEs assigned to an RM,
     * collects their MSME IDs, and then uses this method.
     */
    List<AppUser> findByRoleAndMsmeIdIn(
            String role,
            Collection<Long> msmeIds
    );
}
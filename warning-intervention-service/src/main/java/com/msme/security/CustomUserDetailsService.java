package com.msme.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.msme.entity.AppUser;
import com.msme.repository.AppUserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(
            AppUserRepository appUserRepository) {

        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "Invalid email or password"
                    )
                );

        return new CustomUserPrincipal(user);
    }

    public UserDetails loadUserById(Long userId)
            throws UsernameNotFoundException {

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "User not found"
                    )
                );

        return new CustomUserPrincipal(user);
    }
}
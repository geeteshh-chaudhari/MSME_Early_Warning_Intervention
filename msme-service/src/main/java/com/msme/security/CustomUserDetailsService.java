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
    private final com.msme.repository.MsmeRepository msmes;

    public CustomUserDetailsService(
            AppUserRepository appUserRepository, com.msme.repository.MsmeRepository msmes) {

        this.appUserRepository = appUserRepository;
        this.msmes = msmes;
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

        if ("MSME_REP".equals(user.getRole()) && (user.getMsmeId() == null
                || msmes.findById(user.getMsmeId()).filter(m -> "Y".equals(m.getIsActive())).isEmpty()))
            throw new UsernameNotFoundException("Business account is inactive");
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

        if ("MSME_REP".equals(user.getRole()) && (user.getMsmeId() == null
                || msmes.findById(user.getMsmeId()).filter(m -> "Y".equals(m.getIsActive())).isEmpty()))
            throw new UsernameNotFoundException("Business account is inactive");
        return new CustomUserPrincipal(user);
    }
}
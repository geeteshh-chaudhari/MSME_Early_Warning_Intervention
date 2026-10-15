package com.msme.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.msme.dto.auth.LoginRequest;
import com.msme.dto.auth.LoginResponse;
import com.msme.security.CustomUserPrincipal;
import com.msme.security.JwtService;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getEmail(),
                                request.getPassword()
                        )
                );

        CustomUserPrincipal principal =
                (CustomUserPrincipal) authentication.getPrincipal();

        String token = jwtService.generateToken(principal);

        return new LoginResponse(
                token,
                "Bearer",
                principal.getUserId(),
                principal.getName(),
                principal.getEmail(),
                principal.getRole(),
                principal.getMsmeId()
        );
    }
}
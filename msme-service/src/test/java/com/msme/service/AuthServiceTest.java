package com.msme.service;

import com.msme.dto.auth.LoginRequest;
import com.msme.dto.auth.LoginResponse;
import com.msme.entity.AppUser;
import com.msme.security.CustomUserPrincipal;
import com.msme.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;

    @Test
    void successfulLoginReturnsTokenAndAuthenticatedUserDetails() {
        CustomUserPrincipal principal = principal("MSME_REP", 100L);
        authenticateAs(principal);
        when(jwtService.generateToken(principal)).thenReturn("test-jwt");

        LoginResponse result = service.login(request());

        assertAll(
                () -> assertEquals("test-jwt", result.getAccessToken()),
                () -> assertEquals("Bearer", result.getTokenType()),
                () -> assertEquals(20L, result.getUserId()),
                () -> assertEquals("Test User", result.getName()),
                () -> assertEquals("user@example.com", result.getEmail()),
                () -> assertEquals("MSME_REP", result.getRole()),
                () -> assertEquals(100L, result.getMsmeId())
        );

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertInstanceOf(UsernamePasswordAuthenticationToken.class,
                captor.getValue());
        assertEquals("user@example.com", captor.getValue().getPrincipal());
        assertEquals("Password123!", captor.getValue().getCredentials());
        assertFalse(captor.getValue().isAuthenticated());
        verify(jwtService).generateToken(principal);
    }

    @Test
    void rmLoginDoesNotRequireAssociatedMsme() {
        CustomUserPrincipal principal = principal("RM", null);
        authenticateAs(principal);
        when(jwtService.generateToken(principal)).thenReturn("rm-jwt");

        LoginResponse result = service.login(request());

        assertEquals("rm-jwt", result.getAccessToken());
        assertEquals("RM", result.getRole());
        assertNull(result.getMsmeId());
    }

    @Test
    void invalidCredentialsArePropagatedWithoutGeneratingToken() {
        BadCredentialsException failure =
                new BadCredentialsException("Invalid credentials");
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(failure);

        assertSame(failure, assertThrows(BadCredentialsException.class,
                () -> service.login(request())));

        verifyNoInteractions(jwtService);
    }

    @Test
    void disabledAccountIsRejectedWithoutGeneratingToken() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new DisabledException("Account is disabled"));

        assertThrows(DisabledException.class,
                () -> service.login(request()));

        verifyNoInteractions(jwtService);
    }

    @Test
    void tokenGenerationFailureIsPropagated() {
        CustomUserPrincipal principal = principal("MSME_REP", 100L);
        authenticateAs(principal);
        IllegalStateException failure =
                new IllegalStateException("Token generation failed");
        when(jwtService.generateToken(principal)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.login(request())));
    }

    private LoginRequest request() {
        return new LoginRequest("user@example.com", "Password123!");
    }

    private CustomUserPrincipal principal(String role, Long msmeId) {
        AppUser user = new AppUser();
        user.setUserId(20L);
        user.setName("Test User");
        user.setEmail("user@example.com");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        user.setMsmeId(msmeId);
        user.setAccountStatus("ACTIVE");
        return new CustomUserPrincipal(user);
    }

    private void authenticateAs(CustomUserPrincipal principal) {
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated);
    }
}

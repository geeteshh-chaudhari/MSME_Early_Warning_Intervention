package com.msme.controller;

import com.msme.dto.auth.LoginRequest;
import com.msme.dto.auth.LoginResponse;
import com.msme.security.CustomUserPrincipal;
import com.msme.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final long expirationMs;
    private final boolean secureCookie;
    public AuthController(AuthService authService,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.auth.cookie-secure:false}") boolean secureCookie) {
        this.authService=authService; this.expirationMs=expirationMs; this.secureCookie=secureCookie;
    }
    private ResponseCookie cookie(String name, String value, boolean httpOnly, long age, HttpServletRequest request) {
        return ResponseCookie.from(name,value).httpOnly(httpOnly).secure(secureCookie || request.isSecure())
                .sameSite("Strict").path("/").maxAge(Duration.ofMillis(age)).build();
    }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value="X-Auth-Mode", defaultValue="bearer") String mode, HttpServletRequest http) {
        LoginResponse result=authService.login(request);
        var response=ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store");
        // Legacy non-browser clients keep their existing bearer contract.
        if ("cookie".equals(mode)) {
            response.header(HttpHeaders.SET_COOKIE,cookie("MSME_AUTH",result.getAccessToken(),true,expirationMs,http).toString());
            response.header(HttpHeaders.SET_COOKIE,cookie("MSME_CSRF",UUID.randomUUID().toString(),false,expirationMs,http).toString());
            result.setAccessToken(null);
        }
        return response.body(result);
    }
    @GetMapping("/session")
    public ResponseEntity<LoginResponse> session(@AuthenticationPrincipal CustomUserPrincipal user) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(new LoginResponse(
                null,"Cookie",user.getUserId(),user.getName(),user.getEmail(),user.getRole(),user.getMsmeId()));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                cookie("MSME_AUTH","",true,0,request).toString(),
                cookie("MSME_CSRF","",false,0,request).toString()).build();
    }
}

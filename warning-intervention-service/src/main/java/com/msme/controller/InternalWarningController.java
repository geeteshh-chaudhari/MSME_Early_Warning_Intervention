package com.msme.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.msme.service.WarningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@io.swagger.v3.oas.annotations.Hidden
@RequestMapping("/internal/warnings")
public class InternalWarningController {
    private final WarningService warnings;
    private final String token;
    public InternalWarningController(WarningService warnings, @Value("${app.internal.token}") String token) {
        this.warnings = warnings;
        this.token = token;
    }
    @PostMapping("/assessments/{id}")
    public ResponseEntity<Void> create(@PathVariable Long id,
            @RequestHeader(value="X-Internal-Token", required=false) String supplied) {
        if (token.length() < 32 || supplied == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (id <= 0) return ResponseEntity.badRequest().build();
        warnings.createAutomatic(id);
        return ResponseEntity.noContent().build();
    }
}

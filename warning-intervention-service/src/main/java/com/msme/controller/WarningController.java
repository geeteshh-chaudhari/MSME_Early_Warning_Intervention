package com.msme.controller;

import java.net.URI;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.msme.dto.warning.*;
import com.msme.service.WarningService;

@RestController
@RequestMapping("/api/warnings")
public class WarningController {
    private final WarningService warningService;
    public WarningController(WarningService warningService) { this.warningService = warningService; }

    @PostMapping
    public ResponseEntity<WarningResponse> create(@Valid @RequestBody CreateWarningRequest request) {
        var result = warningService.createWarning(request);
        return ResponseEntity.created(URI.create("/api/warnings/" + result.getWarningId())).body(result);
    }

    @GetMapping
    public ResponseEntity<Page<WarningResponse>> list(@RequestParam Long msmeId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(warningService.getWarnings(msmeId, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarningResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(warningService.getWarning(id));
    }

    @PostMapping("/{id}/decisions")
    public ResponseEntity<WarningResponse> decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return ResponseEntity.ok(warningService.decide(id, request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<Page<ActivityLogResponse>> history(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(warningService.getHistory(id, page, size));
    }
}

package com.msme.controller;
import java.util.Map;
import com.msme.service.*;
import com.msme.dto.warning.WarningResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/warnings/{id}/recommendation")
@PreAuthorize("hasAnyRole('RM', 'RISK_ADMIN')")
public class RecommendationController {
    private final RecommendationWorkflow recommendations;
    public RecommendationController(RecommendationWorkflow recommendations){this.recommendations=recommendations;}
    @GetMapping public Map<String,Object> state(@PathVariable Long id){return recommendations.state(id);}
    @PostMapping
    public WarningResponse generate(@PathVariable Long id){return recommendations.explicitlyGenerate(id);}
}

package com.devopsdays.qoe.api.controllers;

import com.devopsdays.qoe.api.models.ValidationResult;
import com.devopsdays.qoe.api.services.ValidationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/validation")
@RequiredArgsConstructor
@Tag(name = "Validation", description = "Validation rules and run results")
public class ValidationController {

    private final ValidationService validationService;

    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> createRule(@Valid @RequestBody Map<String, Object> ruleData) {
        Map<String, Object> rule = validationService.createRule(ruleData);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<Map<String, Object>>> listRules() {
        List<Map<String, Object>> rules = validationService.getAllRules();
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/run")
    public ResponseEntity<ValidationResult> runValidation(@Valid @RequestBody Map<String, Object> validationRequest) {
        ValidationResult result = validationService.runValidation(validationRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/results")
    public ResponseEntity<List<ValidationResult>> getResults(
            @RequestParam(required = false) String videoId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String sessionId
    ) {
        List<ValidationResult> results = validationService.getResults(videoId, platform, sessionId);
        return ResponseEntity.ok(results);
    }
}

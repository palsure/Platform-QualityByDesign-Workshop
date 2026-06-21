package com.devopsdays.qoe.api.controllers;

import com.devopsdays.qoe.api.models.Platform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/platforms")
@Tag(name = "Platforms", description = "Supported playback platforms")
public class PlatformController {

    @GetMapping
    @Operation(summary = "List all supported platforms",
               description = "Returns every platform the API accepts for metrics, validation, and pipeline-run submissions.")
    public ResponseEntity<List<Map<String, String>>> listPlatforms(
            @RequestParam(required = false) Platform.Category category
    ) {
        List<Map<String, String>> result = Arrays.stream(Platform.values())
                .filter(p -> category == null || p.getCategory() == category)
                .map(p -> Map.of(
                        "key", p.getKey(),
                        "displayName", p.getDisplayName(),
                        "category", p.getCategory().name()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}

package it.elismart_lims.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Simple health check endpoint for the frontend and monitoring tools.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Returns the backend status and current timestamp.
     *
     * @return 200 OK with {@code {"status":"UP","timestamp":"..."}}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}

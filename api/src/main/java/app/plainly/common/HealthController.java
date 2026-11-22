package app.plainly.common;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness check for Railway and local runs: the API is up and can reach Postgres. */
@RestController
class HealthController {

    private final HealthService health;

    HealthController(HealthService health) {
        this.health = health;
    }

    @GetMapping("/api/health")
    ResponseEntity<Map<String, String>> health() {
        return health.databaseUp()
                ? ResponseEntity.ok(Map.of("status", "UP", "database", "UP"))
                : ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "DOWN"));
    }
}

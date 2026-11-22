package app.plainly.common;

import org.springframework.stereotype.Service;

/** Whether the API can reach its database. */
@Service
public class HealthService {

    private final HealthRepository health;

    public HealthService(HealthRepository health) {
        this.health = health;
    }

    public boolean databaseUp() {
        try {
            health.ping();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}

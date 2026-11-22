package app.plainly.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the app reads. Policy days are America/New_York calendar days (DECISIONS.md,
 * 2026-11-22). Setting {@code plainly.today} (env PLAINLY_TODAY) pins "today" for demos and tests.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock(@Value("${plainly.zone}") String zone, @Value("${plainly.today:}") String today) {
        return policyClock(ZoneId.of(zone), today, Clock.systemUTC());
    }

    static Clock policyClock(ZoneId zone, String fixedDay, Clock base) {
        if (fixedDay == null || fixedDay.isBlank()) {
            return base.withZone(zone);
        }
        // Noon, so the calendar day is the same in every nearby zone.
        return Clock.fixed(LocalDate.parse(fixedDay).atTime(12, 0).atZone(zone).toInstant(), zone);
    }
}

package app.plainly.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

// covers AC3, T12
class TodayTest {

    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void todayIsTheNewYorkCalendarDay() {
        // 02:30 UTC on Nov 23 is still Nov 22 in New York (UTC-5).
        Clock utc = Clock.fixed(Instant.parse("2026-11-23T02:30:00Z"), ZoneOffset.UTC);
        Clock clock = ClockConfig.policyClock(NEW_YORK, "", utc);
        assertThat(new Today(clock).date()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void aConfiguredDayOverridesTheRealDate() {
        Clock clock = ClockConfig.policyClock(NEW_YORK, "2026-09-01", Clock.systemUTC());
        assertThat(new Today(clock).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(clock.getZone()).isEqualTo(NEW_YORK);
    }
}

package app.plainly.common;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// covers T12
@IntegrationTest
class TodayWiringTest {

    @Autowired Today today;

    @Test
    void theAppUsesTheConfiguredDay() {
        assertThat(today.date()).isEqualTo(LocalDate.of(2026, 11, 22)); // plainly.today in IntegrationTest
    }
}

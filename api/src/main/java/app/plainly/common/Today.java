package app.plainly.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Today's policy calendar day. Read this instead of LocalDate.now() anywhere in the app. */
@Component
public class Today {

    private final Clock clock;

    public Today(Clock clock) {
        this.clock = clock;
    }

    public LocalDate date() {
        return LocalDate.now(clock);
    }

    /** The policy calendar day a moment fell on (9pm in New York is still that day, not the next UTC day). */
    public LocalDate dayOf(Instant moment) {
        return moment.atZone(clock.getZone()).toLocalDate();
    }
}

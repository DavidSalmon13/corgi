package app.plainly.common;

import java.time.Clock;
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
}

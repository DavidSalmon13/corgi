package app.plainly.billing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code processed_stripe_events}, which makes webhook handling happen once per Stripe event id. */
@Repository
public class ProcessedEventRepository {

    private final JdbcClient jdbc;

    public ProcessedEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records the event id; false if it was already recorded. A second copy arriving at the same moment waits on
     * the first one's row and then gets false, so recording it in the handler's transaction makes a replay a no-op.
     */
    public boolean recordIfNew(String eventId, String type) {
        return jdbc.sql("insert into processed_stripe_events (event_id, type) values (?, ?) on conflict do nothing")
                .params(eventId, type).update() == 1;
    }
}

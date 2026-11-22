package app.plainly.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A round trip to Postgres for the health check. */
@Repository
public class HealthRepository {

    private final JdbcClient jdbc;

    public HealthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Throws if the database can't be reached. */
    public void ping() {
        jdbc.sql("select 1").query(Integer.class).single();
    }
}

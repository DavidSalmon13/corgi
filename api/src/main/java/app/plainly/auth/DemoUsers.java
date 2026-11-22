package app.plainly.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DemoUsers {

    private static final String SELECT = "select id, role, display_name, subtitle, customer_id from app_users";

    private final JdbcClient jdbc;

    public DemoUsers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Brokers first, then policyholders in the order their policies were issued. */
    public List<DemoUser> all() {
        return jdbc.sql(SELECT + " order by case role when 'BROKER' then 0 else 1 end, id").query(DemoUsers::map).list();
    }

    public Optional<DemoUser> find(long id) {
        return jdbc.sql(SELECT + " where id = ?").param(id).query(DemoUsers::map).optional();
    }

    private static DemoUser map(ResultSet rs, int row) throws SQLException {
        return new DemoUser(rs.getLong("id"), Role.valueOf(rs.getString("role")), rs.getString("display_name"),
                rs.getString("subtitle"), rs.getObject("customer_id", Long.class));
    }
}

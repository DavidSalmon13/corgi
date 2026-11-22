package app.plainly.policy;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code coverages}. */
@Repository
public class CoverageRepository {

    private final JdbcClient jdbc;

    public CoverageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long policyId, CoverKind kind, long annualPremiumCents, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                values (?, ?, ?, ?, ?) returning id""")
                .params(policyId, kind.name(), annualPremiumCents, from, to)
                .query(Long.class).single();
    }

    public boolean exists(long policyId, CoverKind kind) {
        return jdbc.sql("select exists (select 1 from coverages where policy_id = ? and kind = ?)")
                .params(policyId, kind.name()).query(Boolean.class).single();
    }

    public List<PolicyViews.Coverage> listForPolicy(long policyId) {
        return jdbc.sql("""
                select id, kind, annual_premium_cents, effective_from, effective_to
                from coverages where policy_id = ? order by id""")
                .param(policyId)
                .query((rs, n) -> {
                    CoverKind kind = CoverKind.valueOf(rs.getString(2));
                    return new PolicyViews.Coverage(rs.getLong(1), kind, kind.label(), rs.getLong(3),
                            rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class));
                })
                .list();
    }
}

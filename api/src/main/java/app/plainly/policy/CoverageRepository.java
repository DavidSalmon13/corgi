package app.plainly.policy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    /** The cover of this kind nobody has replaced. */
    public record Live(long id, long annualPremiumCents, LocalDate from, LocalDate to) {}

    /** The cover of this kind that a correction hasn't replaced, if the policy has one. */
    public Optional<Live> findLive(long policyId, CoverKind kind) {
        return jdbc.sql("""
                select id, annual_premium_cents, effective_from, effective_to from coverages
                where policy_id = ? and kind = ? and replaced_by_coverage_id is null
                order by id desc limit 1""")
                .params(policyId, kind.name())
                .query((rs, n) -> new Live(rs.getLong(1), rs.getLong(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class)))
                .optional();
    }

    /** Points a corrected cover at the row that replaces it. A row is replaced at most once (unique in V8). */
    public void markReplaced(long oldId, long newId) {
        jdbc.sql("update coverages set replaced_by_coverage_id = ? where id = ? and replaced_by_coverage_id is null")
                .params(newId, oldId).update();
    }

    /** Whether the policy has live cover of this kind; a row a correction replaced doesn't count. */
    public boolean exists(long policyId, CoverKind kind) {
        return jdbc.sql("""
                select exists (select 1 from coverages
                               where policy_id = ? and kind = ? and replaced_by_coverage_id is null)""")
                .params(policyId, kind.name()).query(Boolean.class).single();
    }

    /** Live covers only, so a corrected cover shows once, carrying the start it replaced. */
    public List<PolicyViews.Coverage> listForPolicy(long policyId) {
        return jdbc.sql("""
                select c.id, c.kind, c.annual_premium_cents, c.effective_from, c.effective_to,
                       old.effective_from as previous_start
                from coverages c
                left join coverages old on old.replaced_by_coverage_id = c.id
                where c.policy_id = ? and c.replaced_by_coverage_id is null
                order by c.id""")
                .param(policyId)
                .query((rs, n) -> {
                    CoverKind kind = CoverKind.valueOf(rs.getString(2));
                    return new PolicyViews.Coverage(rs.getLong(1), kind, kind.label(), rs.getLong(3),
                            rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class),
                            rs.getObject(6, LocalDate.class));
                })
                .list();
    }
}

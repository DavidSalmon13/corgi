package app.plainly.policy;

import java.sql.ResultSet;
import java.sql.SQLException;
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

    /**
     * The cover of this kind nobody has replaced. {@code to} stays the original end; {@code endedOn} is the day a
     * removal stopped it (start of that day), or null while it runs.
     */
    public record Live(long id, CoverKind kind, long annualPremiumCents, LocalDate from, LocalDate to,
            LocalDate endedOn) {}

    /** The cover of this kind that a correction hasn't replaced, if the policy has one. */
    public Optional<Live> findLive(long policyId, CoverKind kind) {
        return jdbc.sql("""
                select id, kind, annual_premium_cents, effective_from, effective_to, ended_on from coverages
                where policy_id = ? and kind = ? and replaced_by_coverage_id is null
                order by id desc limit 1""")
                .params(policyId, kind.name())
                .query((rs, n) -> live(rs))
                .optional();
    }

    /** Every cover on the policy that a correction hasn't replaced, oldest first (base cover, then extra cover). */
    public List<Live> listLive(long policyId) {
        return jdbc.sql("""
                select id, kind, annual_premium_cents, effective_from, effective_to, ended_on from coverages
                where policy_id = ? and replaced_by_coverage_id is null
                order by id""")
                .param(policyId)
                .query((rs, n) -> live(rs))
                .list();
    }

    private static Live live(ResultSet rs) throws SQLException {
        return new Live(rs.getLong(1), CoverKind.valueOf(rs.getString(2)), rs.getLong(3),
                rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class));
    }

    /** Points a corrected cover at the row that replaces it. A row is replaced at most once (unique in V8). */
    public void markReplaced(long oldId, long newId) {
        jdbc.sql("update coverages set replaced_by_coverage_id = ? where id = ? and replaced_by_coverage_id is null")
                .params(newId, oldId).update();
    }

    /**
     * Ends a cover from this day (a removal). Only once: a cover already ended keeps its day and this returns false.
     * The day must fall inside the cover (coverages_ended_in_cover, V12).
     */
    public boolean markEnded(long id, LocalDate day) {
        return jdbc.sql("update coverages set ended_on = ? where id = ? and ended_on is null")
                .params(day, id).update() == 1;
    }

    /** Live covers only, so a corrected cover shows once, carrying the start it replaced. */
    public List<PolicyViews.Coverage> listForPolicy(long policyId) {
        return jdbc.sql("""
                select c.id, c.kind, c.annual_premium_cents, c.effective_from, c.effective_to,
                       old.effective_from as previous_start, c.ended_on
                from coverages c
                left join coverages old on old.replaced_by_coverage_id = c.id
                where c.policy_id = ? and c.replaced_by_coverage_id is null
                order by c.id""")
                .param(policyId)
                .query((rs, n) -> {
                    CoverKind kind = CoverKind.valueOf(rs.getString(2));
                    return new PolicyViews.Coverage(rs.getLong(1), kind, kind.label(), rs.getLong(3),
                            rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class),
                            rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class));
                })
                .list();
    }
}

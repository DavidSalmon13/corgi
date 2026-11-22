package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerLine;
import app.plainly.ledger.LedgerQueries;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC2, AC3, AC4, AC5, AC6, T8
@IntegrationTest
class IssuePolicyServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest

    @Autowired IssuePolicyService service;
    @Autowired LedgerQueries ledger;
    @Autowired JdbcClient jdbc;

    long brokerId() {
        return jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
    }

    static IssuePolicyCommand juniper(boolean extra) {
        return new IssuePolicyCommand("Juniper & Rye Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                "maya@example.com", SEP_1, extra);
    }

    @Test
    void previewOfBaseCoverIsTheFullYearPrice() {
        IssuePlan plan = service.preview(SEP_1, false);
        assertThat(plan.start()).isEqualTo(SEP_1);
        assertThat(plan.end()).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(plan.lines()).singleElement().satisfies(l -> {
            assertThat(l.kind()).isEqualTo(CoverKind.BASE);
            assertThat(l.description()).isEqualTo("Base cover, full year");
            assertThat(l.calc().annualCents()).isEqualTo(120_000);
            assertThat(l.calc().days()).isEqualTo(365);
            assertThat(l.calc().termDays()).isEqualTo(365);
            assertThat(l.calc().amountCents()).isEqualTo(120_000);
        });
        assertThat(plan.totalCents()).isEqualTo(120_000);
    }

    @Test
    void previewWithExtraCoverAddsAFullYearOfProductLiability() {
        IssuePlan plan = service.preview(SEP_1, true);
        assertThat(plan.lines()).extracting(IssuePlan.Line::kind).containsExactly(CoverKind.BASE, CoverKind.PRODUCT_LIABILITY);
        assertThat(plan.lines().get(1).description()).isEqualTo("Extra cover, full year");
        assertThat(plan.lines().get(1).calc().amountCents()).isEqualTo(73_000);
        assertThat(plan.totalCents()).isEqualTo(193_000);
    }

    @Test
    void aTermSpanningFeb29Has366Days() {
        IssuePlan plan = service.preview(LocalDate.of(2027, 9, 1), false);
        assertThat(plan.lines().getFirst().calc().termDays()).isEqualTo(366);
        assertThat(plan.totalCents()).isEqualTo(120_000);
    }

    @Test
    void previewSavesNothing() {
        long before = count("customers") + count("policies") + count("ledger_entries");
        service.preview(SEP_1, true);
        assertThat(count("customers") + count("policies") + count("ledger_entries")).isEqualTo(before);
    }

    @Test
    void previewNeedsAStartDate() {
        assertThatThrownBy(() -> service.preview(null, false))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.fields()).containsOnlyKeys("startDate"));
    }

    @Test
    void issuingWritesThePolicyItsCoversOneTransactionAndOneChargePerCover() {
        IssuePlan preview = service.preview(SEP_1, true);
        IssuePolicyService.Issued issued = service.issue(juniper(true), brokerId());

        assertThat(issued.policyNumber()).matches("GL-2026-\\d{4}");
        assertThat(issued.transactionDisplayId()).matches("PTX-\\d{4,}");

        var policy = jdbc.sql("select status, start_date, end_date from policies where id = ?").param(issued.policyId())
                .query((rs, n) -> List.of(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate())).single();
        assertThat(policy).containsExactly("ACTIVE", SEP_1, LocalDate.of(2027, 9, 1));

        assertThat(jdbc.sql("select kind || ':' || annual_premium_cents || ':' || effective_from || ':' || effective_to from coverages where policy_id = ? order by id")
                .param(issued.policyId()).query(String.class).list())
                .containsExactly("BASE:120000:2026-09-01:2027-09-01", "PRODUCT_LIABILITY:73000:2026-09-01:2027-09-01");

        var tx = jdbc.sql("select type, effective_date, recorded_on, created_by from policy_transactions where policy_id = ?")
                .param(issued.policyId())
                .query((rs, n) -> List.of(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getLong(4)))
                .single();
        assertThat(tx).containsExactly("ISSUE", SEP_1, TODAY, brokerId());

        List<LedgerLine> lines = ledger.lines(issued.policyId());
        assertThat(lines).extracting(LedgerLine::amountCents)
                .containsExactlyElementsOf(preview.lines().stream().map(l -> l.calc().amountCents()).toList());
        assertThat(lines).allSatisfy(l -> {
            assertThat(l.policyTransactionDisplayId()).isEqualTo(issued.transactionDisplayId());
            assertThat(l.entryDate()).isEqualTo(TODAY);
            assertThat(l.coverageId()).isNotNull();
            assertThat(l.calcDays()).isEqualTo(365);
        });
        assertThat(ledger.balance(issued.policyId())).isEqualTo(preview.totalCents()).isEqualTo(issued.totalCents());
    }

    @Test
    void issuingMakesTheOwnerAPolicyholderWhoCanSignIn() {
        IssuePolicyService.Issued issued = service.issue(juniper(false), brokerId());
        var user = jdbc.sql("""
                select u.display_name, u.subtitle from app_users u join policies p on p.customer_id = u.customer_id
                where p.id = ? and u.role = 'POLICYHOLDER'""")
                .param(issued.policyId()).query((rs, n) -> List.of(rs.getString(1), rs.getString(2))).single();
        assertThat(user).containsExactly("Maya Okafor", "Owner, Juniper & Rye Bakery LLC");
    }

    @Test
    void everyMissingFieldGetsItsOwnMessageAndNothingIsSaved() {
        long before = count("customers");
        assertThatThrownBy(() -> service.issue(new IssuePolicyCommand(" ", "", null, "", null, false), brokerId()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields())
                        .containsOnlyKeys("legalName", "businessDescription", "ownerName", "ownerEmail", "startDate")
                        .containsEntry("legalName", "Enter the business's legal name.")
                        .containsEntry("ownerEmail", "Enter an email like name@business.com.")
                        .containsEntry("startDate", "Pick the day cover should start."));
        assertThat(count("customers")).isEqualTo(before);
    }

    @Test
    void anEmailWithoutADomainIsRejected() {
        var cmd = new IssuePolicyCommand("Juniper & Rye Bakery LLC", "a bakery", "Maya Okafor", "maya@juniperandrye", SEP_1, false);
        assertThatThrownBy(() -> service.issue(cmd, brokerId()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsOnlyKeys("ownerEmail"));
    }

    @Test
    void aStartDateMoreThanAYearFromTodayIsRejected() {
        assertThatThrownBy(() -> service.preview(TODAY.plusYears(1).plusDays(1), false))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsOnlyKeys("startDate"));
        assertThatThrownBy(() -> service.preview(TODAY.minusYears(1).minusDays(1), false))
                .isInstanceOf(ValidationException.class);
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }
}

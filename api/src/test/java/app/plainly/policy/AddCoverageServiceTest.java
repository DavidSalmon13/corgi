package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import app.plainly.ledger.EntryType;
import app.plainly.ledger.LedgerLine;
import app.plainly.ledger.LedgerQueryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

// covers AC3, AC4, AC5, AC7, T1
@IntegrationTest
class AddCoverageServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate POLICY_END = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest

    @Autowired AddCoverageService service;
    @Autowired IssuePolicyService issuer;
    @Autowired LedgerQueryService ledger;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    long brokerId() {
        return jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
    }

    /** A base-cover-only policy, Sep 1, 2026 → Sep 1, 2027 unless another start is given. */
    long policy(LocalDate start) {
        return issuer.issue(new IssuePolicyCommand("Juniper & Rye Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                TestData.uniqueEmail("maya"), start, false), brokerId()).policyId();
    }

    long policy() {
        return policy(SEP_1);
    }

    static AddCoverageCommand productLiabilityFrom(LocalDate start) {
        return new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, start);
    }

    // --- pricing (AC3) ---

    @Test
    void fromOct15ItCharges321DaysOfTheYearlyPrice() {
        long id = policy();
        AddCoveragePlan plan = service.preview(id, CoverKind.PRODUCT_LIABILITY, OCT_15);
        assertThat(plan.policyId()).isEqualTo(id);
        assertThat(plan.kind()).isEqualTo(CoverKind.PRODUCT_LIABILITY);
        assertThat(plan.label()).isEqualTo("Product liability endorsement from Oct 15");
        assertThat(plan.from()).isEqualTo(OCT_15);
        assertThat(plan.to()).isEqualTo(POLICY_END);
        assertThat(plan.calc().annualCents()).isEqualTo(73_000);
        assertThat(plan.calc().days()).isEqualTo(321);
        assertThat(plan.calc().termDays()).isEqualTo(365);
        assertThat(plan.calc().amountCents()).isEqualTo(64_200);
        assertThat(plan.totalCents()).isEqualTo(64_200);
    }

    @Test
    void startingOnTheLastDayChargesOneDay() {
        AddCoveragePlan plan = service.preview(policy(), CoverKind.PRODUCT_LIABILITY, POLICY_END.minusDays(1));
        assertThat(plan.calc().days()).isEqualTo(1);
        assertThat(plan.totalCents()).isEqualTo(200);
    }

    @Test
    void startingOnThePolicyStartChargesTheFullYear() {
        AddCoveragePlan plan = service.preview(policy(), CoverKind.PRODUCT_LIABILITY, SEP_1);
        assertThat(plan.calc().days()).isEqualTo(365);
        assertThat(plan.totalCents()).isEqualTo(73_000);
    }

    @Test
    void aTermSpanningFeb29Divides366Days() {
        long id = policy(LocalDate.of(2027, 9, 1)); // → Sep 1, 2028, which spans Feb 29, 2028
        AddCoveragePlan plan = service.preview(id, CoverKind.PRODUCT_LIABILITY, LocalDate.of(2027, 10, 15));
        assertThat(plan.calc().termDays()).isEqualTo(366);
        assertThat(plan.calc().days()).isEqualTo(322);
        assertThat(plan.totalCents()).isEqualTo(64_224); // 73,000 × 322 ÷ 366 = 64,224.04, rounded once
    }

    @Test
    void previewSavesNothing() {
        long id = policy();
        long before = writes();
        service.preview(id, CoverKind.PRODUCT_LIABILITY, OCT_15);
        assertThat(writes()).isEqualTo(before);
    }

    // --- validation (AC4) ---

    @Test
    void aMissingStartDateIsRejected() {
        assertFieldError(() -> service.preview(policy(), CoverKind.PRODUCT_LIABILITY, null),
                "startDate", "Pick the endorsement's effective date.");
    }

    @Test
    void aStartBeforeThePolicyIsRejected() {
        assertFieldError(() -> service.preview(policy(), CoverKind.PRODUCT_LIABILITY, SEP_1.minusDays(1)),
                "startDate", "The effective date can't be before the policy period starts (Sep 1, 2026).");
    }

    @Test
    void aStartOnOrAfterThePolicyEndIsRejected() {
        long id = policy();
        assertFieldError(() -> service.preview(id, CoverKind.PRODUCT_LIABILITY, POLICY_END),
                "startDate", "The effective date has to be before the policy period ends (Sep 1, 2027).");
        assertFieldError(() -> service.preview(id, CoverKind.PRODUCT_LIABILITY, POLICY_END.plusDays(10)),
                "startDate", "The effective date has to be before the policy period ends (Sep 1, 2027).");
    }

    @Test
    void anyCoverOtherThanProductLiabilityIsRejected() {
        long id = policy();
        assertFieldError(() -> service.preview(id, CoverKind.BASE, OCT_15), "kind", "Pick a cover to add.");
        assertFieldError(() -> service.preview(id, null, OCT_15), "kind", "Pick a cover to add.");
    }

    @Test
    void aPolicyThatAlreadyHasProductLiabilityIsRejected() {
        long id = policy();
        service.add(id, productLiabilityFrom(OCT_15), brokerId());
        assertFieldError(() -> service.preview(id, CoverKind.PRODUCT_LIABILITY, LocalDate.of(2026, 12, 1)),
                "kind", "This policy already has product liability coverage.");

        long issuedWithIt = issuer.issue(new IssuePolicyCommand("Both Covers LLC", "a shop", "Bo Owner",
                TestData.uniqueEmail("bo"), SEP_1, true), brokerId()).policyId();
        assertFieldError(() -> service.preview(issuedWithIt, CoverKind.PRODUCT_LIABILITY, OCT_15),
                "kind", "This policy already has product liability coverage.");
    }

    @Test
    void aCancelledPolicyCannotTakeCover() {
        long id = policy();
        jdbc.sql("update policies set status = 'CANCELLED', cancelled_on = date '2026-12-01' where id = ?").param(id).update();
        assertFieldError(() -> service.preview(id, CoverKind.PRODUCT_LIABILITY, OCT_15),
                "policy", "This policy is cancelled, so cover can't be added.");
        assertFieldError(() -> service.add(id, productLiabilityFrom(OCT_15), brokerId()),
                "policy", "This policy is cancelled, so cover can't be added.");
    }

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.preview(Long.MAX_VALUE, CoverKind.PRODUCT_LIABILITY, OCT_15))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.add(Long.MAX_VALUE, productLiabilityFrom(OCT_15), brokerId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anInvalidAddSavesNothing() {
        long id = policy();
        long before = writes();
        assertThatThrownBy(() -> service.add(id, productLiabilityFrom(null), brokerId()))
                .isInstanceOf(ValidationException.class);
        assertThat(writes()).isEqualTo(before);
    }

    // --- adding (AC5) ---

    @Test
    void addingRecordsOneTransactionOneCoverAndOneLinkedCharge() {
        long id = policy();
        long balanceBefore = ledger.balance(id);
        AddCoveragePlan preview = service.preview(id, CoverKind.PRODUCT_LIABILITY, OCT_15);

        AddCoverageService.Added added = service.add(id, productLiabilityFrom(OCT_15), brokerId());

        assertThat(added.policyId()).isEqualTo(id);
        assertThat(added.transactionDisplayId()).matches("PTX-\\d{4,}");
        assertThat(added.totalCents()).isEqualTo(preview.totalCents()).isEqualTo(64_200);

        var txs = jdbc.sql("""
                select display_id, effective_date, recorded_on, created_by from policy_transactions
                where policy_id = ? and type = 'ADD_COVERAGE'""")
                .param(id)
                .query((rs, n) -> List.of(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getLong(4)))
                .list();
        assertThat(txs).containsExactly(List.of(added.transactionDisplayId(), OCT_15, TODAY, brokerId()));

        var covers = jdbc.sql("""
                select id, annual_premium_cents || ':' || effective_from || ':' || effective_to from coverages
                where policy_id = ? and kind = 'PRODUCT_LIABILITY'""")
                .param(id).query((rs, n) -> List.of(rs.getLong(1), rs.getString(2))).list();
        assertThat(covers).singleElement().satisfies(c -> assertThat(c.get(1)).isEqualTo("73000:2026-10-15:2027-09-01"));
        long coverageId = (Long) covers.getFirst().get(0);

        List<LedgerLine> newLines = ledger.lines(id).stream()
                .filter(l -> added.transactionDisplayId().equals(l.policyTransactionDisplayId())).toList();
        assertThat(newLines).singleElement().satisfies(l -> {
            assertThat(l.displayId()).isEqualTo(added.entries().getFirst().displayId());
            assertThat(l.type()).isEqualTo(EntryType.CHARGE);
            assertThat(l.entryDate()).isEqualTo(TODAY);
            assertThat(l.description()).isEqualTo("Product liability endorsement from Oct 15");
            assertThat(l.amountCents()).isEqualTo(64_200);
            assertThat(l.coverageId()).isEqualTo(coverageId);
            assertThat(l.calcAnnualCents()).isEqualTo(73_000L);
            assertThat(l.calcDays()).isEqualTo(321);
            assertThat(l.calcTermDays()).isEqualTo(365);
            assertThat(l.coverFrom()).isEqualTo(OCT_15);
            assertThat(l.coverTo()).isEqualTo(POLICY_END);
        });
        assertThat(added.entries()).hasSize(1);

        assertThat(ledger.balance(id)).isEqualTo(balanceBefore + preview.totalCents()).isEqualTo(added.balanceCents());
    }

    // --- one extra cover, even when two brokers click at once (AC7) ---

    @Test
    void twoSimultaneousAddsMakeOneCoverAndOneCharge() throws Exception {
        long id = policy();
        long broker = brokerId();
        long balanceBefore = ledger.balance(id);
        CountDownLatch go = new CountDownLatch(1);
        Callable<AddCoverageService.Added> add = () -> {
            go.await();
            return service.add(id, productLiabilityFrom(OCT_15), broker);
        };

        List<Future<AddCoverageService.Added>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(add));
            results.add(pool.submit(add));
            go.countDown();
        }

        int saved = 0;
        List<Throwable> rejected = new ArrayList<>();
        for (Future<AddCoverageService.Added> r : results) {
            try {
                r.get();
                saved++;
            } catch (ExecutionException e) {
                rejected.add(e.getCause());
            }
        }
        assertThat(saved).isEqualTo(1);
        assertThat(rejected).singleElement().isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsExactly(
                        java.util.Map.entry("kind", "This policy already has product liability coverage.")));
        assertThat(jdbc.sql("select count(*) from coverages where policy_id = ? and kind = 'PRODUCT_LIABILITY'")
                .param(id).query(Long.class).single()).isEqualTo(1);
        assertThat(ledger.balance(id)).isEqualTo(balanceBefore + 64_200);
    }

    /**
     * The race the lock exists for, made deterministic: another transaction holds the policy row, the add
     * starts and waits on it, then the other transaction adds the cover and commits. The waiting add must see
     * that cover once it gets the lock. A check made in the same statement as the lock reads the database from
     * before the wait and would let a second cover and charge through.
     */
    @Test
    void anAddThatWaitedOnTheLockSeesTheCoverAddedMeanwhile() throws Exception {
        long id = policy();
        long broker = brokerId();
        long balanceBefore = ledger.balance(id);
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<AddCoverageService.Added> waiting = tx.execute(status -> {
                jdbc.sql("select id from policies where id = ? for update").param(id).query(Long.class).single();
                Future<AddCoverageService.Added> f = pool.submit(() -> service.add(id, productLiabilityFrom(OCT_15), broker));
                awaitBlockedOnLock();
                jdbc.sql("""
                        insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                        values (?, 'PRODUCT_LIABILITY', 73000, ?, ?)""").params(id, OCT_15, POLICY_END).update();
                return f;
            });
            assertThatThrownBy(waiting::get).cause().isInstanceOfSatisfying(ValidationException.class,
                    e -> assertThat(e.fields()).containsExactly(
                            java.util.Map.entry("kind", "This policy already has product liability coverage.")));
        }
        assertThat(jdbc.sql("select count(*) from coverages where policy_id = ? and kind = 'PRODUCT_LIABILITY'")
                .param(id).query(Long.class).single()).isEqualTo(1);
        assertThat(ledger.balance(id)).isEqualTo(balanceBefore);
    }

    /** Waits until another session is waiting for a lock (pg_locks, which unlike pg_stat_activity isn't cached per transaction), so the add is known to be queued behind ours. */
    private void awaitBlockedOnLock() {
        for (int i = 0; i < 200; i++) {
            long waiting = jdbc.sql("""
                    select count(*) from pg_locks where not granted""").query(Long.class).single();
            if (waiting > 0) return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("the add never waited on the policy lock");
    }

    private static void assertFieldError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field,
            String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsOnlyKeys(field).containsEntry(field, message));
    }

    private long writes() {
        return jdbc.sql("""
                select (select count(*) from coverages) + (select count(*) from policy_transactions)
                     + (select count(*) from ledger_entries)""")
                .query(Long.class).single();
    }
}

package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers remove-cover AC4, AC7, T4
/** The SQL a removal needs: end a cover once, on a day inside it, and read that day back on every cover view. */
@IntegrationTest
class CoverRemovalRepositoryTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate SEP_1_NEXT = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);

    @Autowired JdbcClient jdbc;
    @Autowired CoverageRepository coverages;

    long policyId;
    long base;
    long extra;

    @BeforeEach
    void policyWithProductLiability() {
        policyId = TestData.policy(jdbc, SEP_1, SEP_1_NEXT);
        base = coverages.insert(policyId, CoverKind.BASE, 120_000, SEP_1, SEP_1_NEXT);
        extra = coverages.insert(policyId, CoverKind.PRODUCT_LIABILITY, 73_000, OCT_8, SEP_1_NEXT);
    }

    @Test
    void aRunningCoverHasNoEndedDay() {
        assertThat(coverages.findLive(policyId, CoverKind.PRODUCT_LIABILITY)).get()
                .satisfies(c -> assertThat(c.endedOn()).isNull());
        assertThat(coverages.listForPolicy(policyId)).allSatisfy(c -> assertThat(c.endedOn()).isNull());
    }

    @Test
    void endingSetsTheDayOnceAndKeepsTheOriginalEnd() {
        assertThat(coverages.markEnded(extra, DEC_1)).isTrue();
        assertThat(coverages.markEnded(extra, OCT_8)).as("a second removal changes nothing").isFalse();

        assertThat(coverages.findLive(policyId, CoverKind.PRODUCT_LIABILITY)).get().satisfies(c -> {
            assertThat(c.id()).isEqualTo(extra);
            assertThat(c.endedOn()).isEqualTo(DEC_1);
            assertThat(c.to()).isEqualTo(SEP_1_NEXT);
        });
        assertThat(coverages.listLive(policyId)).containsExactly(
                new CoverageRepository.Live(base, CoverKind.BASE, 120_000, SEP_1, SEP_1_NEXT, null),
                new CoverageRepository.Live(extra, CoverKind.PRODUCT_LIABILITY, 73_000, OCT_8, SEP_1_NEXT, DEC_1));
    }

    @Test
    void theBrokerAndCustomerViewsCarryTheEndedDay() {
        coverages.markEnded(extra, DEC_1);

        assertThat(coverages.listForPolicy(policyId)).extracting(PolicyViews.Coverage::endedOn)
                .containsExactly(null, DEC_1);
    }

    @Test
    void endingOnTheCoversStartDayIsAllowed() {
        assertThat(coverages.markEnded(extra, OCT_8)).isTrue();
    }

    @Test
    void theDatabaseRefusesAnEndedDayOutsideTheCover() {
        assertThatThrownBy(() -> coverages.markEnded(extra, OCT_8.minusDays(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> coverages.markEnded(extra, SEP_1_NEXT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseAcceptsARemovalTransaction() {
        assertThat(TestData.transaction(jdbc, policyId, "REMOVE_COVERAGE", DEC_1)).isPositive();
    }
}

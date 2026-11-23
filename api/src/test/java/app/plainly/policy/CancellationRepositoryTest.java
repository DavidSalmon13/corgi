package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC7, AC8, AC9, T4
/** The SQL a cancellation needs: mark the policy cancelled once, read its cancel day, and list its live covers. */
@IntegrationTest
class CancellationRepositoryTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate SEP_1_NEXT = LocalDate.of(2027, 9, 1);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);

    @Autowired JdbcClient jdbc;
    @Autowired PolicyRepository policies;
    @Autowired CoverageRepository coverages;

    long policyId;

    @BeforeEach
    void activePolicy() {
        policyId = TestData.policy(jdbc, SEP_1, SEP_1_NEXT);
    }

    @Test
    void anActivePolicyHasNoCancelDay() {
        assertThat(policies.findTerm(policyId, false)).get().satisfies(t -> {
            assertThat(t.status()).isEqualTo("ACTIVE");
            assertThat(t.cancelledOn()).isNull();
        });
    }

    @Test
    void cancellingSetsTheDayOnceAndKeepsTheTerm() {
        assertThat(policies.markCancelled(policyId, DEC_1)).isTrue();
        assertThat(policies.markCancelled(policyId, SEP_1)).as("a second cancellation changes nothing").isFalse();

        assertThat(policies.findTerm(policyId, true)).get().satisfies(t -> {
            assertThat(t.status()).isEqualTo("CANCELLED");
            assertThat(t.cancelledOn()).isEqualTo(DEC_1);
            assertThat(t.end()).isEqualTo(SEP_1_NEXT);
        });
        assertThat(policies.findHead(policyId)).get().satisfies(h -> assertThat(h.cancelledOn()).isEqualTo(DEC_1));
    }

    @Test
    void liveCoversSkipTheRowsACorrectionReplaced() {
        long base = coverages.insert(policyId, CoverKind.BASE, 120_000, SEP_1, SEP_1_NEXT);
        long oldExtra = coverages.insert(policyId, CoverKind.PRODUCT_LIABILITY, 73_000, LocalDate.of(2026, 10, 15), SEP_1_NEXT);
        long newExtra = coverages.insert(policyId, CoverKind.PRODUCT_LIABILITY, 73_000, LocalDate.of(2026, 10, 8), SEP_1_NEXT);
        coverages.markReplaced(oldExtra, newExtra);

        assertThat(coverages.listLive(policyId)).containsExactly(
                new CoverageRepository.Live(base, CoverKind.BASE, 120_000, SEP_1, SEP_1_NEXT, null),
                new CoverageRepository.Live(newExtra, CoverKind.PRODUCT_LIABILITY, 73_000, LocalDate.of(2026, 10, 8), SEP_1_NEXT, null));
    }
}

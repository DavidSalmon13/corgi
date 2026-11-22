package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import app.plainly.ledger.LedgerPostingService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// covers AC5, T1: adding cover is one database transaction
@IntegrationTest
class AddCoverageAtomicityTest {

    @MockitoBean LedgerPostingService poster;
    @Autowired AddCoverageService service;
    @Autowired JdbcClient jdbc;

    @Test
    void ifTheChargeCannotBePostedNoTransactionOrCoverIsLeftBehind() {
        // The poster is mocked, so this policy has no ledger lines, which doesn't matter here.
        long policyId = TestData.policy(jdbc, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1));
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' limit 1").query(Long.class).single();
        when(poster.post(anyLong(), any(), any())).thenThrow(new IllegalStateException("ledger down"));

        assertThatThrownBy(() -> service.add(policyId,
                new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, LocalDate.of(2026, 10, 15)), broker))
                .hasMessageContaining("ledger down");
        assertThat(jdbc.sql("""
                select (select count(*) from coverages where policy_id = ?)
                     + (select count(*) from policy_transactions where policy_id = ?)""")
                .params(policyId, policyId).query(Long.class).single()).isZero();
    }
}

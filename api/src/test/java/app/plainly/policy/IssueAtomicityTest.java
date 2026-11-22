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

// covers AC6, T8: issuing is one database transaction
@IntegrationTest
class IssueAtomicityTest {

    @MockitoBean LedgerPostingService poster;
    @Autowired IssuePolicyService service;
    @Autowired JdbcClient jdbc;

    @Test
    void ifTheLedgerCannotBePostedNoCustomerPolicyOrTransactionIsLeftBehind() {
        when(poster.post(anyLong(), any(), any())).thenThrow(new IllegalStateException("ledger down"));
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' limit 1").query(Long.class).single();
        long before = rows();

        assertThatThrownBy(() -> service.issue(new IssuePolicyCommand("Atomic Bakery LLC", "a bakery", "Ann Owner",
                TestData.uniqueEmail("ann"), LocalDate.of(2026, 9, 1), true), broker))
                .hasMessageContaining("ledger down");
        assertThat(rows()).isEqualTo(before);
    }

    private long rows() {
        return jdbc.sql("""
                select (select count(*) from customers) + (select count(*) from policies) + (select count(*) from coverages)
                     + (select count(*) from policy_transactions) + (select count(*) from app_users)""")
                .query(Long.class).single();
    }
}

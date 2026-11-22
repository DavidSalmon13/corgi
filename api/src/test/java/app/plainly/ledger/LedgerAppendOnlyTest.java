package app.plainly.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC7, T6
/** AC7: the database itself refuses to change or remove a ledger line. */
@IntegrationTest
class LedgerAppendOnlyTest {

    @Autowired JdbcClient jdbc;

    long entryId;

    @BeforeEach
    void postOneEntry() {
        long customerId = jdbc.sql("""
                insert into customers (legal_name, business_description, owner_name, owner_email)
                values ('Trigger Test LLC', 'a test shop', 'Test Owner', ?) returning id""")
                .param(TestData.uniqueEmail("owner")).query(Long.class).single();
        long policyId = jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date)
                values ('TEST-' || nextval('policy_number_seq'), ?, date '2026-09-01', date '2027-09-01') returning id""")
                .param(customerId).query(Long.class).single();
        long ptxId = jdbc.sql("""
                insert into policy_transactions (policy_id, type, effective_date)
                values (?, 'ISSUE', date '2026-09-01') returning id""")
                .param(policyId).query(Long.class).single();
        entryId = jdbc.sql("""
                insert into ledger_entries (policy_id, policy_transaction_id, entry_type, entry_date, description, amount_cents)
                values (?, ?, 'CHARGE', date '2026-09-01', 'Base cover, full year', 120000) returning id""")
                .params(policyId, ptxId).query(Long.class).single();
    }

    @Test
    void updateIsRefused() {
        assertThatThrownBy(() -> jdbc.sql("update ledger_entries set amount_cents = 1 where id = ?").param(entryId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(amount()).isEqualTo(120_000);
    }

    @Test
    void deleteIsRefused() {
        assertThatThrownBy(() -> jdbc.sql("delete from ledger_entries where id = ?").param(entryId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(amount()).isEqualTo(120_000);
    }

    @Test
    void truncateIsRefused() {
        assertThatThrownBy(() -> jdbc.sql("truncate ledger_entries cascade").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void entriesGetReadableDisplayIds() {
        String displayId = jdbc.sql("select display_id from ledger_entries where id = ?").param(entryId)
                .query(String.class).single();
        assertThat(displayId).matches("LE-\\d{4,}");
    }

    private long amount() {
        return jdbc.sql("select amount_cents from ledger_entries where id = ?").param(entryId).query(Long.class).single();
    }
}

package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Who made each change on a policy, for the "What caused it" part of P4 (the name, not the account id). */
// covers AC7, T2
@IntegrationTest
class PolicyActorNamesTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    @Autowired PolicyService policies;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired JdbcClient jdbc;

    long sam() {
        return jdbc.sql("select id from app_users where role = 'BROKER' and display_name = 'Sam Reyes'")
                .query(Long.class).single();
    }

    /** A second broker, so a policy's changes can come from different people. */
    long anotherBroker(String name) {
        return jdbc.sql("insert into app_users (role, display_name, email) values ('BROKER', ?, ?) returning id")
                .params(name, TestData.uniqueEmail("broker")).query(Long.class).single();
    }

    List<String> transactionIds(long policyId) {
        return policies.detail(policyId).transactions().stream().map(PolicyViews.Transaction::displayId).toList();
    }

    @Test
    void eachChangeIsNamedAfterTheBrokerWhoMadeIt() {
        long riley = anotherBroker("Riley Chen");
        long id = issuer.issue(new IssuePolicyCommand("Two Brokers Bakery LLC", "a bakery", "Ann Owner",
                TestData.uniqueEmail("ann"), SEP_1, false), sam()).policyId();
        adder.add(id, new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, OCT_15), riley);
        List<String> txs = transactionIds(id);

        assertThat(policies.actorNames(id)).containsExactly(
                java.util.Map.entry(txs.get(0), "Sam Reyes"),
                java.util.Map.entry(txs.get(1), "Riley Chen"));
    }

    @Test
    void aChangeWithNoRecordedAuthorIsLeftOut() {
        long id = issuer.issue(new IssuePolicyCommand("Unknown Author LLC", "a shop", "Al Owner",
                TestData.uniqueEmail("al"), SEP_1, false), sam()).policyId();
        TestData.transaction(jdbc, id, "ADD_COVERAGE", OCT_15); // created_by is null

        assertThat(policies.actorNames(id)).hasSize(1).containsValue("Sam Reyes");
    }

    @Test
    void aPolicyWithNoChangesHasNoNames() {
        assertThat(policies.actorNames(Long.MAX_VALUE)).isEmpty();
    }
}

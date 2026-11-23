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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

// covers more-policies AC3, AC4, AC6, AC9, T3, T4
/** The SQL behind several policies per customer: find a customer, link a rewrite to what it replaces, list and own. */
@IntegrationTest
class PolicyRewriteRepositoryTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Autowired JdbcClient jdbc;
    @Autowired PolicyRepository policies;
    @Autowired CustomerRepository customers;

    long customerId;
    PolicyRepository.Created old;

    @BeforeEach
    void aCustomerWithOnePolicy() {
        customerId = customers.insert("Rewrite Repo LLC", "a bakery", "Rae Repo", TestData.uniqueEmail("rewrite-repo"));
        old = policies.insert(customerId, SEP_1, SEP_1.plusYears(1));
    }

    PolicyRepository.Created rewriteOld() {
        policies.markCancelled(old.id(), SEP_1);
        return policies.insert(customerId, OCT_1, OCT_1.plusYears(1), old.id());
    }

    @Test
    void findsACustomerByIdAndNothingForAnUnknownOne() {
        assertThat(customers.find(customerId)).get().satisfies(c -> {
            assertThat(c.id()).isEqualTo(customerId);
            assertThat(c.legalName()).isEqualTo("Rewrite Repo LLC");
            assertThat(c.businessDescription()).isEqualTo("a bakery");
            assertThat(c.ownerName()).isEqualTo("Rae Repo");
            assertThat(c.ownerEmail()).startsWith("rewrite-repo-");
        });
        assertThat(customers.find(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void aRewriteRecordsWhatItReplacesAndBothPoliciesShowTheLink() {
        var rewrite = rewriteOld();

        assertThat(policies.findHead(rewrite.id())).get().satisfies(h -> {
            assertThat(h.customerId()).isEqualTo(customerId);
            assertThat(h.replaces()).isEqualTo(new PolicyViews.PolicyLink(old.id(), old.policyNumber()));
            assertThat(h.replacedBy()).isNull();
        });
        assertThat(policies.findHead(old.id())).get().satisfies(h -> {
            assertThat(h.replaces()).isNull();
            assertThat(h.replacedBy()).isEqualTo(new PolicyViews.PolicyLink(rewrite.id(), rewrite.policyNumber()));
        });
        assertThat(policies.listRows()).filteredOn(r -> r.id() == old.id()).singleElement().satisfies(r -> {
            assertThat(r.customerId()).isEqualTo(customerId);
            assertThat(r.replacedBy()).isEqualTo(new PolicyViews.PolicyLink(rewrite.id(), rewrite.policyNumber()));
        });
    }

    @Test
    void aPolicyIsReplacedAtMostOnceAndNeverByItself() {
        rewriteOld();

        assertThatThrownBy(() -> policies.insert(customerId, OCT_1, OCT_1.plusYears(1), old.id()))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.sql("update policies set replaces_policy_id = id where id = ?").param(old.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void locksTheReplacedPolicyAndReadsWhatTheRulesNeed() {
        policies.markCancelled(old.id(), SEP_1);

        assertThat(policies.lockReplaceTarget(old.id())).get().isEqualTo(
                new PolicyRepository.ReplaceTarget(old.id(), customerId, old.policyNumber(), "CANCELLED", SEP_1, null));

        var rewrite = policies.insert(customerId, OCT_1, OCT_1.plusYears(1), old.id());
        assertThat(policies.lockReplaceTarget(old.id())).get()
                .extracting(PolicyRepository.ReplaceTarget::replacedByNumber).isEqualTo(rewrite.policyNumber());
        assertThat(policies.lockReplaceTarget(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void listsACustomersPoliciesNewestFirstWithTheirLinks() {
        var rewrite = rewriteOld();
        policies.insert(customers.insert("Someone Else LLC", "a shop", "Sam Else", TestData.uniqueEmail("else")), OCT_1,
                OCT_1.plusYears(1));

        assertThat(policies.listForCustomer(customerId)).extracting(PolicyRepository.CustomerPolicy::id)
                .containsExactly(rewrite.id(), old.id());
        assertThat(policies.listForCustomer(customerId).get(0)).satisfies(p -> {
            assertThat(p.number()).isEqualTo(rewrite.policyNumber());
            assertThat(p.start()).isEqualTo(OCT_1);
            assertThat(p.end()).isEqualTo(OCT_1.plusYears(1));
            assertThat(p.status()).isEqualTo("ACTIVE");
            assertThat(p.paymentWaiting()).isFalse();
            assertThat(p.replaces()).isEqualTo(new PolicyViews.PolicyLink(old.id(), old.policyNumber()));
        });
        assertThat(policies.listForCustomer(customerId).get(1)).satisfies(p -> {
            assertThat(p.status()).isEqualTo("CANCELLED");
            assertThat(p.cancelledOn()).isEqualTo(SEP_1);
            assertThat(p.replacedBy()).isEqualTo(new PolicyViews.PolicyLink(rewrite.id(), rewrite.policyNumber()));
        });
    }

    @Test
    void ownsOnlyItsOwnPolicies() {
        long other = customers.insert("Not Mine LLC", "a shop", "Nia Other", TestData.uniqueEmail("not-mine"));

        assertThat(policies.isOwnedBy(old.id(), customerId)).isTrue();
        assertThat(policies.isOwnedBy(old.id(), other)).isFalse();
        assertThat(policies.isOwnedBy(Long.MAX_VALUE, customerId)).isFalse();
    }
}

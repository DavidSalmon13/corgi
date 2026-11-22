package app.plainly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import app.plainly.TestData;
import app.plainly.common.ValidationException;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers real-sign-in AC1, AC2, AC4, AC5, T6
@IntegrationTest
class AccountServiceTest {

    @Autowired AccountService accounts;
    @Autowired IssuePolicyService issue;
    @Autowired JdbcClient jdbc;

    String ownerEmail;

    @BeforeEach
    void issuedPolicy() {
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        ownerEmail = TestData.uniqueEmail("acct");
        issue.issue(new IssuePolicyCommand("Accounts Shop LLC", "a shop", "Ari Owner", ownerEmail, LocalDate.of(2026, 9, 1), false), broker);
    }

    @Test
    void theSeededBrokerSignsInWithTheDemoPasswordOnly() {
        assertThat(accounts.signIn(" Sam@Plainly.test", "plainly-demo-2026")).hasValueSatisfying(u -> {
            assertThat(u.role()).isEqualTo(Role.BROKER);
            assertThat(u.email()).isEqualTo("sam@plainly.test");
        });
        assertThat(accounts.signIn("sam@plainly.test", "wrong")).isEmpty();
        assertThat(accounts.signIn("nobody@example.com", "plainly-demo-2026")).isEmpty();
    }

    @Test
    void signingUpClaimsTheWaitingAccountOnce() {
        assertThat(accounts.signIn(ownerEmail, "ten-chars-ok")).as("no password yet").isEmpty();
        SignedInUser user = accounts.signUp(ownerEmail.toUpperCase(), "ten-chars-ok", "ten-chars-ok");
        assertThat(user.role()).isEqualTo(Role.POLICYHOLDER);
        assertThat(user.displayName()).isEqualTo("Ari Owner");
        assertThat(accounts.signIn(ownerEmail, "ten-chars-ok")).contains(user);

        assertThatThrownBy(() -> accounts.signUp(ownerEmail, "another-one-1", "another-one-1"))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.fields()).containsExactlyEntriesOf(java.util.Map.of("email", AccountService.ALREADY_SIGNED_UP)));
    }

    @Test
    void everyProblemIsListedAndNothingChanges() {
        assertThatThrownBy(() -> accounts.signUp(ownerEmail, "short", "shorter"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields())
                        .containsOnlyKeys("password", "confirmPassword"));
        assertThatThrownBy(() -> accounts.signUp("", null, null))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsOnlyKeys("email", "password"));
        assertThatThrownBy(() -> accounts.signUp("stranger@example.com", "ten-chars-ok", "ten-chars-ok"))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.fields()).containsEntry("email", AccountService.NO_POLICY));
        assertThatThrownBy(() -> accounts.signUp(ownerEmail, "x".repeat(73), "x".repeat(73)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsOnlyKeys("password"));
        assertThat(jdbc.sql("select count(*) from app_users where email = ? and password_hash is null")
                .param(ownerEmail).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aBrokerEmailIsNeverClaimable() {
        assertThatThrownBy(() -> accounts.signUp("sam@plainly.test", "taking-over", "taking-over"))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.fields()).containsEntry("email", AccountService.ALREADY_SIGNED_UP));
    }
}

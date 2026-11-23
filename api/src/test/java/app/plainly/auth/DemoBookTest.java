package app.plainly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// The reviewers' book of business: every story seeds through the real services, and seeding twice adds nothing
@IntegrationTest
class DemoBookTest {

    @Autowired DemoSeeder seeder;
    @Autowired AccountService accounts;
    @Autowired JdbcClient jdbc;

    @Test
    void everyStorySeedsOnceWithTheLifecycleStepItShows() {
        seeder.seedBook();
        seeder.seedBook();

        assertThat(policies("tomas@herreracycle.test")).containsExactly("ACTIVE");
        assertThat(transactions("tomas@herreracycle.test")).containsExactly("ISSUE", "ADD_COVERAGE");
        assertThat(transactions("priya@saffronlane.test")).containsExactly("ISSUE", "ADD_COVERAGE", "CORRECT");
        assertThat(transactions("owen@brightsidegrooming.test")).containsExactly("ISSUE", "REMOVE_COVERAGE");
        assertThat(policies("lena@kowalskiprint.test")).containsExactly("CANCELLED");
        assertThat(policies("daniel@achebetailoring.test")).containsExactlyInAnyOrder("CANCELLED", "ACTIVE");
        assertThat(policies("grace@marigoldtea.test")).containsExactly("ACTIVE");

        assertThat(accounts.signIn("priya@saffronlane.test", DemoSeeder.DEMO_PASSWORD)).isPresent();
        assertThat(accounts.hasSignedUp("owen@brightsidegrooming.test")).isFalse();
    }

    private List<String> policies(String email) {
        return jdbc.sql("select p.status from policies p join customers c on c.id = p.customer_id where c.owner_email = ?")
                .param(email).query(String.class).list();
    }

    private List<String> transactions(String email) {
        return jdbc.sql("""
                select t.type from policy_transactions t join policies p on p.id = t.policy_id
                join customers c on c.id = p.customer_id where c.owner_email = ? order by t.id""")
                .param(email).query(String.class).list();
    }
}

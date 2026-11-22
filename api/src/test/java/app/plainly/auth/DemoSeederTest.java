package app.plainly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers real-sign-in AC12, T9 — both demo accounts sign in, and seeding twice adds nothing
@IntegrationTest
class DemoSeederTest {

    @Autowired DemoSeeder seeder;
    @Autowired AccountService accounts;
    @Autowired JdbcClient jdbc;

    @Test
    void seedingGivesMayaAPolicyFromSamAndAnAccountAndRunsOnlyOnce() {
        seeder.seed();
        seeder.seed();

        assertThat(accounts.signIn(DemoSeeder.BROKER_EMAIL, DemoSeeder.DEMO_PASSWORD)).isPresent();
        assertThat(accounts.signIn(DemoSeeder.POLICYHOLDER_EMAIL, DemoSeeder.DEMO_PASSWORD)).hasValueSatisfying(u -> {
            assertThat(u.role()).isEqualTo(Role.POLICYHOLDER);
            assertThat(u.displayName()).isEqualTo("Maya Okafor");
            assertThat(u.subtitle()).isEqualTo("Owner, Juniper & Rye Bakery LLC");
        });
        assertThat(jdbc.sql("select count(*) from customers where owner_email = ?")
                .param(DemoSeeder.POLICYHOLDER_EMAIL).query(Integer.class).single()).isEqualTo(1);
        // plainly.today is 2026-11-22 in tests; the policy started three weeks earlier
        assertThat(jdbc.sql("""
                select p.start_date from policies p join customers c on c.id = p.customer_id where c.owner_email = ?""")
                .param(DemoSeeder.POLICYHOLDER_EMAIL).query(LocalDate.class).single()).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}

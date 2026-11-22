package app.plainly.auth;

import app.plainly.common.Today;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for reviewers (real-sign-in AC12): Maya Okafor of Juniper &amp; Rye Bakery LLC, with a
 * policy issued by Sam three weeks ago and an account she has already signed up for. Fictional
 * people, test credentials only. Runs at startup when {@code plainly.demo.seed} is true, only adds
 * what's missing, and is off in tests. The broker, Sam, is seeded by the V5 migration.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    /** The password of every demo account; V5 stores Sam's hash of the same value. */
    public static final String DEMO_PASSWORD = "plainly-demo-2026";
    public static final String BROKER_EMAIL = "sam@plainly.test";
    public static final String POLICYHOLDER_EMAIL = "maya@juniperandrye.test";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final boolean enabled;
    private final JdbcClient jdbc;
    private final IssuePolicyService issue;
    private final Passwords passwords;
    private final Today today;

    public DemoSeeder(@Value("${plainly.demo.seed:false}") boolean enabled, JdbcClient jdbc, IssuePolicyService issue,
            Passwords passwords, Today today) {
        this.enabled = enabled;
        this.jdbc = jdbc;
        this.issue = issue;
        this.passwords = passwords;
        this.today = today;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    @Transactional
    public void seed() {
        boolean issued = jdbc.sql("select exists (select 1 from app_users where email = ?)")
                .param(POLICYHOLDER_EMAIL).query(Boolean.class).single();
        if (!issued) {
            long sam = jdbc.sql("select id from app_users where email = ?").param(BROKER_EMAIL).query(Long.class).single();
            issue.issue(new IssuePolicyCommand("Juniper & Rye Bakery LLC", "A neighborhood bakery and café", "Maya Okafor",
                    POLICYHOLDER_EMAIL, today.date().minusDays(21), false), sam);
            log.info("Demo seed: issued Maya Okafor's policy for Juniper & Rye Bakery LLC");
        }
        int signedUp = jdbc.sql("""
                update app_users set password_hash = ?, signed_up_at = now()
                where email = ? and password_hash is null""")
                .params(passwords.hash(DEMO_PASSWORD), POLICYHOLDER_EMAIL).update();
        if (signedUp == 1) {
            log.info("Demo seed: Maya Okafor can sign in as {}", POLICYHOLDER_EMAIL);
        }
    }
}

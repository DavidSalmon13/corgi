package app.plainly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Full Spring context against a real Postgres (Testcontainers). The context, and so the
 * container, is shared across test classes. Ledger rows can't be deleted, so tests never clean
 * up: each test creates its own data and asserts on it by id. Stripe is a fake ({@link FakeStripeGateway});
 * webhook signatures are checked for real with a test secret ({@link StripeTestSupport}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import({TestcontainersConfiguration.class, StripeTestSupport.Config.class})
@TestPropertySource(properties = {"plainly.today=2026-11-22", "plainly.demo.seed=false", "plainly.refunds.sweep=false",
        "plainly.stripe.webhook-secret=" + StripeTestSupport.WEBHOOK_SECRET,
        "plainly.stripe.publishable-key=" + StripeTestSupport.PUBLISHABLE_KEY,
        "plainly.stripe.secret-key=sk_test_plainly_test_only"})
public @interface IntegrationTest {}

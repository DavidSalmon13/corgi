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
 * up: each test creates its own data and asserts on it by id.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "plainly.today=2026-11-22")
public @interface IntegrationTest {}

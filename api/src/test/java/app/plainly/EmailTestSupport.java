package app.plainly;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Registers {@link FakeEmailGateway} in place of Resend on every {@link IntegrationTest} context. */
public final class EmailTestSupport {

    private EmailTestSupport() {}

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        @Primary
        FakeEmailGateway fakeEmailGateway() {
            return new FakeEmailGateway();
        }
    }
}

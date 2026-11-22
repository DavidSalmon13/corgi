package app.plainly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// covers real-sign-in AC9, T5
class PasswordsTest {

    final Passwords passwords = new Passwords();

    @Test
    void aHashIsBcryptAndOnlyTheRightPasswordMatchesIt() {
        String hash = passwords.hash("bread-and-butter-42");
        assertThat(hash).startsWith("$2").doesNotContain("bread");
        assertThat(passwords.matches("bread-and-butter-42", hash)).isTrue();
        assertThat(passwords.matches("bread-and-butter-43", hash)).isFalse();
        assertThat(passwords.hash("bread-and-butter-42")).as("salted").isNotEqualTo(hash);
    }

    @Test
    void noHashNullOrOverlongPasswordsNeverMatch() {
        String hash = passwords.hash("bread-and-butter-42");
        assertThat(passwords.matches("anything", null)).isFalse();
        assertThat(passwords.matches(null, hash)).isFalse();
        assertThat(passwords.matches("x".repeat(73), hash)).isFalse();
    }

    @Test
    void passwordsOverBcryptsLimitAreRefusedNotTruncated() {
        assertThat(Passwords.tooLong("x".repeat(72))).isFalse();
        assertThat(Passwords.tooLong("x".repeat(73))).isTrue();
        assertThat(Passwords.tooLong("é".repeat(37))).as("74 bytes").isTrue();
        assertThatThrownBy(() -> passwords.hash("x".repeat(73))).isInstanceOf(IllegalArgumentException.class);
    }
}

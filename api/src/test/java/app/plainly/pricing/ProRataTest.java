package app.plainly.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProRataTest {

    static final LocalDate SEP_1_2026 = LocalDate.of(2026, 9, 1);
    static final LocalDate SEP_1_2027 = LocalDate.of(2027, 9, 1);

    @Test
    void fullYearChargesTheWholeAnnualPremium() {
        ProRata.Charge c = ProRata.charge(120_000, SEP_1_2026, SEP_1_2027, SEP_1_2026, SEP_1_2027);
        assertThat(c.days()).isEqualTo(365);
        assertThat(c.termDays()).isEqualTo(365);
        assertThat(c.amountCents()).isEqualTo(120_000);
    }

    @Test
    void endDateIsNotCounted() {
        ProRata.Charge c = ProRata.charge(73_000, SEP_1_2026, LocalDate.of(2026, 9, 2), SEP_1_2026, SEP_1_2027);
        assertThat(c.days()).isEqualTo(1);
        assertThat(c.amountCents()).isEqualTo(200); // $2.00 a day
    }

    @Test
    void extraCoverFromOct15Is321DaysAndExactly642Dollars() {
        ProRata.Charge c = ProRata.charge(73_000, LocalDate.of(2026, 10, 15), SEP_1_2027, SEP_1_2026, SEP_1_2027);
        assertThat(c.days()).isEqualTo(321);
        assertThat(c.amountCents()).isEqualTo(64_200);
    }

    @Test
    void extraCoverFromOct8Is328DaysAndExactly656Dollars() {
        ProRata.Charge c = ProRata.charge(73_000, LocalDate.of(2026, 10, 8), SEP_1_2027, SEP_1_2026, SEP_1_2027);
        assertThat(c.days()).isEqualTo(328);
        assertThat(c.amountCents()).isEqualTo(65_600);
    }

    @Test
    void roundsHalfUpOnceToTheCent() {
        // 120000 * 274 / 365 = 90082.19... -> 90082
        assertThat(ProRata.charge(120_000, LocalDate.of(2026, 12, 1), SEP_1_2027, SEP_1_2026, SEP_1_2027).amountCents())
                .isEqualTo(90_082);
        // 100 * 1 / 8 = 12.5 -> 13 (half rounds up)
        LocalDate s = LocalDate.of(2026, 1, 1);
        assertThat(ProRata.charge(100, s, s.plusDays(1), s, s.plusDays(8)).amountCents()).isEqualTo(13);
    }

    @Test
    void leapTermHas366Days() {
        LocalDate start = LocalDate.of(2027, 9, 1);
        LocalDate end = LocalDate.of(2028, 9, 1); // spans Feb 29, 2028
        ProRata.Charge full = ProRata.charge(120_000, start, end, start, end);
        assertThat(full.termDays()).isEqualTo(366);
        assertThat(full.amountCents()).isEqualTo(120_000);
        ProRata.Charge oneDay = ProRata.charge(73_000, start, start.plusDays(1), start, end);
        assertThat(oneDay.amountCents()).isEqualTo(199); // 73000 / 366 = 199.45
    }

    @Test
    void rejectsSpansOutsideTheTermOrEmpty() {
        assertThatThrownBy(() -> ProRata.charge(100, SEP_1_2026.minusDays(1), SEP_1_2027, SEP_1_2026, SEP_1_2027))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProRata.charge(100, SEP_1_2026, SEP_1_2027.plusDays(1), SEP_1_2026, SEP_1_2027))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProRata.charge(100, SEP_1_2026, SEP_1_2026, SEP_1_2026, SEP_1_2027))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProRata.charge(-1, SEP_1_2026, SEP_1_2027, SEP_1_2026, SEP_1_2027))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

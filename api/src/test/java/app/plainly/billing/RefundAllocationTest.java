package app.plainly.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// covers AC5 (correct-effective-date), AC3, AC7 (cancel-refund), T3
/** How money owed back is split over the payments that took it: newest first, capped per payment. */
class RefundAllocationTest {

    static final PaymentService.Collected FIRST = new PaymentService.Collected(1, "PAY-0001", "4242", 120_000);
    static final PaymentService.Collected LATEST = new PaymentService.Collected(2, "PAY-0002", "4242", 64_200);

    @Test
    void theCancellationRefundTakesTheLatestPaymentFirstThenTheRest() {
        assertThat(RefundService.allocate(143_482, List.of(LATEST, FIRST), Map.of())).containsExactly(
                new PlannedRefund(2, "PAY-0002", "4242", 64_200),
                new PlannedRefund(1, "PAY-0001", "4242", 79_282));
    }

    @Test
    void whatAPaymentAlreadyGaveBackIsTakenOffItsRoom() {
        assertThat(RefundService.allocate(10_000, List.of(LATEST, FIRST), Map.of(2L, 60_000L))).containsExactly(
                new PlannedRefund(2, "PAY-0002", "4242", 4_200),
                new PlannedRefund(1, "PAY-0001", "4242", 5_800));
    }

    @Test
    void anythingBeyondWhatWasCollectedStaysAsCredit() {
        assertThat(RefundService.allocate(500_000, List.of(LATEST), Map.of()))
                .containsExactly(new PlannedRefund(2, "PAY-0002", "4242", 64_200));
        assertThat(RefundService.allocate(500, List.of(), Map.of())).isEmpty();
    }
}

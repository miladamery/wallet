package ir.milad.customer.domain.wallet;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
class Borrower {
    SettlementDelay value;

    public static Borrower of(SettlementDelay delay) {
        return new Borrower(delay);
    }
}

package ir.milad.customer.domain.wallet;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
class Lender {
    SettlementDelay value;

    public static Lender of(SettlementDelay delay) {
        return new Lender(delay);
    }
}

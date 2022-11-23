package ir.milad.customer.domain.wallet;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class Balance {
    private Long value;

    public Balance minus(Money money) {
        return new Balance(value - money.value());
    }

    public Balance plus(Money money) {
        return new Balance(value + money.value());
    }

    public boolean isGreaterThan(Money money) {
        return value > money.value();
    }

    public boolean isLessThan(Money money) {
        return value < money.value();
    }
}

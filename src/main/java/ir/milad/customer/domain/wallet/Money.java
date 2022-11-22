package ir.milad.customer.domain.wallet;

import lombok.Value;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Value
public class Money {
    public static final Money ZERO = new Money(0L);

    public static Money of(Long value) {
        return new Money(value);
    }
    Long value;

    private Money(Long value) {
        if (value < 0)
            throw new IllegalArgumentException("Money can't have negative value");
        this.value = value;
    }

    public boolean isGreaterThan(Money another) {
        return value > another.value;
    }

    public boolean isGreaterThanOrEqual(Money another) {
        return value >= another.value;
    }

    public boolean isLowerThanOrEqual(Money another) {
        return value <= another.value;
    }

    public boolean isNotZero() {
        return value != 0;
    }

    public Money minus(Money money) {
        return new Money(value - money.value);
    }

    public Money minus(Long value) {
        return new Money(this.value - value);
    }

    public Money plus(Money money) {
        return new Money(value + money.value);
    }

    public Money plus(Long money) {
        return new Money(value + money);
    }
}

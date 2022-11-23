package ir.milad.customer.domain.wallet;

import lombok.Value;

import java.util.function.Function;
import java.util.function.Supplier;

@Value
public class OptionalDebt {
    private Money money;
    private boolean isInDebt;

    public OptionalDebt(Money money) {
        this.money = money;
        isInDebt = money != null && money != Money.ZERO;
    }

    private OptionalDebt(Money money, boolean isInDebt) {
        this.money = money;
        this.isInDebt = isInDebt;
    }
    public OptionalDebt ifInDebt(Function<Money, Money> inInDebtFunction) {
        if (isInDebt)
            return new OptionalDebt(inInDebtFunction.apply(money), true);
        return this;
    }

    public Money orElse(Money another) {
        return isInDebt ? money : another;
    }
}

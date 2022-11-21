package ir.milad.customer.domain.wallet;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.LinkedHashMap;
import java.util.Map;

public class Wallet {
    private final Map<SettlementDelay, InternalWallet> delayWallets;

    private final Table<Lender, Borrower, Money> higherWalletsToLowerWalletsDebt;

    public Wallet() {
        higherWalletsToLowerWalletsDebt = HashBasedTable.create(4, 4);
        delayWallets = new LinkedHashMap<>(4);
        delayWallets.put(SettlementDelay.T_PLUS_0, new InternalWallet());
        delayWallets.put(SettlementDelay.T_PLUS_1, new InternalWallet());
        delayWallets.put(SettlementDelay.T_PLUS_2, new InternalWallet());
        delayWallets.put(SettlementDelay.T_PLUS_3, new InternalWallet());
    }

    public void block(Money toBlock, SettlementDelay delay) {
        throwIfNotEnoughBuyingPower(toBlock, delay);

        for (SettlementDelay d : delay.EqualAndLess()) {
            if (internalWalletHasEnoughBuyingPower(d, toBlock))
                blockFromInternalWalletWithDebtTracking(d.asLender(), delay.asBorrower(), toBlock);
            else if (internalWalletBuyingPowerIsNotZero(d)) {
                var amount = Money.of(delayWallets.get(d).buyingPower().value());
                toBlock = toBlock.minus(amount);
                blockFromInternalWalletWithDebtTracking(d.asLender(), delay.asBorrower(), amount);
            }
        }
    }

    public void charge(Money money) {
        delayWallets.get(SettlementDelay.T_PLUS_0).deposit(money);
    }

    public void deposit(Money money, SettlementDelay delay) {
        for (SettlementDelay d : delay.lessThan()) {
            var debt = higherWalletsToLowerWalletsDebt.get(d.asLender(), delay.asBorrower());
            if (debt != null && debt != Money.ZERO)
                if (debt.isGreaterThanOrEqual(money)) {
                    higherWalletsToLowerWalletsDebt.put(d.asLender(), delay.asBorrower(), debt.minus(money));
                    delayWallets.get(d).deposit(money);
                    money = Money.ZERO;
                    break;
                } else {
                    deposit(debt, d);
                    money = money.minus(debt);
                }
        }
        delayWallets.get(delay).deposit(money);
    }

    public void withdraw(Money withdraw, SettlementDelay delay) {
    }

    public void spend(Money toSpend, SettlementDelay delay) {
        var blocked = Money.of(delayWallets.get(delay).getBlocked().value());
        throwIfToSpendIsMoreThanWalletBlockCapability(toSpend, delay, blocked);

        if (toSpend.isGreaterThan(blocked)) {
            delayWallets.get(delay).spend(blocked);
            toSpend = toSpend.minus(blocked);
        } else {
            delayWallets.get(delay).spend(toSpend);
            return;
        }

        for (SettlementDelay d: delay.lessThan().reverse()) {
            var debt = higherWalletsToLowerWalletsDebt.get(d.asLender(), delay.asBorrower());
            if (debt != null && debt != Money.ZERO)
                if (debt.isGreaterThan(toSpend)) {
                    delayWallets.get(d).spend(toSpend);
                    higherWalletsToLowerWalletsDebt.put(d.asLender(), delay.asBorrower(), debt.minus(toSpend));
                    break;
                } else {
                    delayWallets.get(d).spend(debt);
                    higherWalletsToLowerWalletsDebt.put(d.asLender(), delay.asBorrower(), Money.ZERO);
                    toSpend = toSpend.minus(debt);
                }
        }
    }

    public void unblock(Money toUnblock, SettlementDelay delay) {
        for (SettlementDelay d : delay.lessThan()) {
            var debt = higherWalletsToLowerWalletsDebt.get(d.asLender(), delay.asBorrower());
            if (debt != null && debt != Money.ZERO)
                if (debt.isGreaterThan(toUnblock)) {
                    delayWallets.get(d).unblock(toUnblock);
                    higherWalletsToLowerWalletsDebt.put(d.asLender(), delay.asBorrower(), debt.minus(toUnblock));
                    toUnblock = Money.ZERO;
                    break;
                } else {
                    unblock(debt, d);
                    toUnblock = toUnblock.minus(debt);
                }
        }

        if (toUnblock != Money.ZERO)
            delayWallets.get(delay).unblock(toUnblock);
    }

    public Money buyingPower(SettlementDelay delay) {
        return Money.of(
                (Long) delay
                        .EqualAndLess()
                        .map(delayWallets::get)
                        .map(internalWallet -> internalWallet.buyingPower().value())
                        .sum()
        );
    }

    private boolean internalWalletHasEnoughBuyingPower(SettlementDelay delay, Money toBlock) {
        return delayWallets.get(delay).buyingPower().isGreaterThan(toBlock);
    }

    private void throwIfNotEnoughBuyingPower(Money block, SettlementDelay delay) {
        var buyingPower = buyingPower(delay);
        if (block.isGreaterThan(buyingPower))
            throw new InsufficientFundsException(String.format("Required %s for blocking but had %s", block.value(), buyingPower.value()));
    }

    private void blockFromInternalWalletWithDebtTracking(Lender lender, Borrower borrower, Money debt) {
        delayWallets.get(lender.value()).block(debt);
        if (borrower.value() != lender.value()) {
            var previousDebt = higherWalletsToLowerWalletsDebt.get(lender, borrower);
            if (previousDebt == null)
                previousDebt = Money.ZERO;
            higherWalletsToLowerWalletsDebt.put(lender, borrower, previousDebt.plus(debt));
        }
    }

    private boolean internalWalletBuyingPowerIsNotZero(SettlementDelay delay) {
        return delayWallets.get(delay).buyingPower().isGreaterThan(Money.ZERO);
    }

    private void throwIfToSpendIsMoreThanWalletBlockCapability(Money toSpend, SettlementDelay delay, Money blocked) {
        var totalPossibleToSpend = delay
                .lessThan()
                .map(d -> higherWalletsToLowerWalletsDebt.get(d.asLender(), delay.asBorrower()))
                .fold(blocked, (acc, money) -> acc.plus(money == null ? Money.ZERO : money));
        if (toSpend.isGreaterThan(totalPossibleToSpend))
            throw new InsufficientFundsException(String.format("Required %s for spending but have only %s", toSpend.value(), totalPossibleToSpend.value()));
    }
}

package ir.milad.customer.domain.wallet;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.Map;

public class Wallet {
    private final Map<SettlementDelay, InternalWallet> delayWallets;
    private final Table<Lender, Borrower, Money> higherWalletsToLowerWalletsDebt;

    public Wallet() {
        higherWalletsToLowerWalletsDebt = HashBasedTable.create(4, 4);
        delayWallets = Map.of(
                SettlementDelay.T_PLUS_0, new InternalWallet(),
                SettlementDelay.T_PLUS_1, new InternalWallet(),
                SettlementDelay.T_PLUS_2, new InternalWallet(),
                SettlementDelay.T_PLUS_3, new InternalWallet()
        );
    }

    public void block(Money toBlock, SettlementDelay highestDelay) {
        throwIfNotEnoughBuyingPower(toBlock, highestDelay);

        for (SettlementDelay delay : highestDelay.EqualAndLess()) {
            if (internalWalletHasEnoughBuyingPower(delay, toBlock))
                blockFromInternalWalletWithDebtTracking(delay.asLender(), highestDelay.asBorrower(), toBlock);
            else if (internalWalletBuyingPowerIsNotZero(delay)) {
                var amount = Money.of(delayWallets.get(delay).buyingPower().value());
                toBlock = toBlock.minus(amount);
                blockFromInternalWalletWithDebtTracking(delay.asLender(), highestDelay.asBorrower(), amount);
            }
        }
    }

    public void charge(Money money) {
        delayWallets.get(SettlementDelay.T_PLUS_0).deposit(money);
    }

    public void deposit(Money money, SettlementDelay delay) {
        for (SettlementDelay lender : delay.lessThan()) {
            var debt = debtToLender(delay, lender);
            if (debt.isGreaterThanOrEqual(money)) {
                // Debt functionality: reducing debt
                higherWalletsToLowerWalletsDebt.put(lender.asLender(), delay.asBorrower(), debt.minus(money));
                delayWallets.get(lender).deposit(money);
                money = Money.ZERO;
            } else if (debt.isNotZero()) {
                deposit(debt, lender);
                money = money.minus(debt);
            }
        }
        delayWallets.get(delay).deposit(money);
    }

    public void withdraw(Money withdraw, SettlementDelay delay) {
    }

    public void spend(Money toSpend, SettlementDelay delay) {
        throwIfToSpendIsMoreThanWalletBlockedMoney(toSpend, delay);

        var blocked = Money.of(delayWallets.get(delay).getBlocked().value());
        if (toSpend.isLowerThanOrEqual(blocked)) {
            delayWallets.get(delay).spend(toSpend);
            return;
        }

        delayWallets.get(delay).spend(blocked);
        toSpend = toSpend.minus(blocked);

        for (SettlementDelay lender : delay.lessThan().reverse()) {
            var debt = debtToLender(delay, lender);
            if (debt.isGreaterThan(toSpend)) {
                // Debt functionality: reducing debt
                higherWalletsToLowerWalletsDebt.put(lender.asLender(), delay.asBorrower(), debt.minus(toSpend));
                delayWallets.get(lender).spend(toSpend);
            } else if (debt.isNotZero()) {
                // Debt functionality: clearing debt
                higherWalletsToLowerWalletsDebt.put(lender.asLender(), delay.asBorrower(), Money.ZERO);
                delayWallets.get(lender).spend(debt);
                toSpend = toSpend.minus(debt);
            }
        }
    }

    public void unblock(Money toUnblock, SettlementDelay delay) {
        for (SettlementDelay lender : delay.lessThan()) {
            var debt = debtToLender(delay, lender);
            if (debt.isGreaterThan(toUnblock)) {
                delayWallets.get(lender).unblock(toUnblock);
                higherWalletsToLowerWalletsDebt.put(lender.asLender(), delay.asBorrower(), debt.minus(toUnblock));
                toUnblock = Money.ZERO;
            } else if (debt.isNotZero()) {
                unblock(debt, lender);
                toUnblock = toUnblock.minus(debt);
            }
        }

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
            // Debt functionality: getting debt
            var previousDebt = higherWalletsToLowerWalletsDebt.get(lender, borrower);
            if (previousDebt == null)
                previousDebt = Money.ZERO;
            // Debt functionality: increasing debt
            higherWalletsToLowerWalletsDebt.put(lender, borrower, previousDebt.plus(debt));
        }
    }

    private boolean internalWalletBuyingPowerIsNotZero(SettlementDelay delay) {
        return delayWallets.get(delay).buyingPower().isGreaterThan(Money.ZERO);
    }

    private void throwIfToSpendIsMoreThanWalletBlockedMoney(Money toSpend, SettlementDelay delay) {
        var blocked = Money.of(delayWallets.get(delay).getBlocked().value());
        var totalPossibleToSpend = delay
                .lessThan()
                .map(d -> higherWalletsToLowerWalletsDebt.get(d.asLender(), delay.asBorrower()))
                .fold(blocked, (acc, money) -> acc.plus(money == null ? Money.ZERO : money));
        if (toSpend.isGreaterThan(totalPossibleToSpend))
            throw new InsufficientFundsException(String.format("Required %s for spending but have only %s", toSpend.value(), totalPossibleToSpend.value()));
    }

    private Money debtToLender(SettlementDelay walletDelay, SettlementDelay lender) {
        // Debt functionality: reducing debt
        var debt = higherWalletsToLowerWalletsDebt.get(lender.asLender(), walletDelay.asBorrower());
        if (debt == null)
            debt = Money.ZERO;
        return debt;
    }
}

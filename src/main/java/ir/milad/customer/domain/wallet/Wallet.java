package ir.milad.customer.domain.wallet;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.Map;
import java.util.Objects;

public class Wallet {
    private final Map<SettlementDelay, InternalWallet> delayWallets;

    private final DebtSupervisor debtSupervisor;

    public Wallet() {
        delayWallets = Map.of(
                SettlementDelay.T_PLUS_0, new InternalWallet(),
                SettlementDelay.T_PLUS_1, new InternalWallet(),
                SettlementDelay.T_PLUS_2, new InternalWallet(),
                SettlementDelay.T_PLUS_3, new InternalWallet()
        );
        debtSupervisor = new DebtSupervisor();
    }

    public void block(Money toBlock, SettlementDelay highestDelay) {
        Objects.requireNonNull(toBlock);
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

    public void deposit(Money remaining, SettlementDelay delay) {
        Objects.requireNonNull(remaining);
        remaining = settleDepositBorrowerDebtsAndReturnRemaining(remaining, delay);
        delayWallets.get(delay).deposit(remaining);
    }

    private Money settleDepositBorrowerDebtsAndReturnRemaining(Money money, SettlementDelay delay) {
        for (SettlementDelay lender : delay.lessThan()) {
            Money _money = money;
            money = borrowerToLenderPossibleDebt(delay, lender)
                    .ifInDebt(debt -> {
                        if (debt.isGreaterThanOrEqual(_money))
                            return depositToLenderAndDecreaseBorrowerDebt(lender, delay, _money);
                        depositAndClearDebt(delay, lender, debt);
                        return _money.minus(debt);
                    })
                    .orElse(money);
        }
        return money;
    }

    private void depositAndClearDebt(SettlementDelay delay, SettlementDelay lender, Money debt) {
        debtSupervisor.clear(lender, delay);
        deposit(debt, lender);
    }

    private Money depositToLenderAndDecreaseBorrowerDebt(SettlementDelay lender, SettlementDelay borrower, Money _money) {
        debtSupervisor.decrease(lender, borrower, _money);
        delayWallets.get(lender).deposit(_money);
        return Money.ZERO;
    }

    public void withdraw(Money withdraw, SettlementDelay delay) {
    }

    public void spend(Money toSpend, SettlementDelay delay) {
        Objects.requireNonNull(toSpend);
        throwIfToSpendIsMoreThanWalletBlockedMoney(toSpend, delay);

        var blocked = Money.of(delayWallets.get(delay).getBlocked().value());
        if (toSpend.isLowerThanOrEqual(blocked)) {
            delayWallets.get(delay).spend(toSpend);
            return;
        }

        delayWallets.get(delay).spend(blocked);
        toSpend = toSpend.minus(blocked);

        spendRemainingFromLowerWallets(toSpend, delay);
    }

    private void spendRemainingFromLowerWallets(Money remaining, SettlementDelay delay) {
        for (SettlementDelay lender : delay.lessThan().reverse()) {
            Money _toSpend = remaining;
            remaining = borrowerToLenderPossibleDebt(delay, lender)
                    .ifInDebt(debt -> {
                        if (debt.isGreaterThan(_toSpend))
                            return spendFromLenderAndDecreaseBorrowerDebt(lender, delay, _toSpend);

                        spendFromLenderAndClearBorrowerDebt(lender, delay, debt);
                        return _toSpend.minus(debt);
                    })
                    .orElse(remaining);
        }
    }

    private void spendFromLenderAndClearBorrowerDebt(SettlementDelay lender, SettlementDelay borrower, Money debt) {
        debtSupervisor.clear(lender, borrower);
        delayWallets.get(lender).spend(debt);
    }

    private Money spendFromLenderAndDecreaseBorrowerDebt(SettlementDelay lender, SettlementDelay borrower, Money _toSpend) {
        debtSupervisor.decrease(lender, borrower, _toSpend);
        delayWallets.get(lender).spend(_toSpend);
        return Money.ZERO;
    }

    public void unblock(Money toUnblock, SettlementDelay delay) {
        Objects.requireNonNull(toUnblock);
        toUnblock = settleUnblockBorrowerDebtsAndReturnRemaining(toUnblock, delay);
        delayWallets.get(delay).unblock(toUnblock);
    }

    private Money settleUnblockBorrowerDebtsAndReturnRemaining(Money money, SettlementDelay delay) {
        for (SettlementDelay lender : delay.lessThan()) {
            Money _money = money;
            money = borrowerToLenderPossibleDebt(delay, lender)
                    .ifInDebt(debt -> {
                        if (debt.isGreaterThan(_money))
                            return unblockFromLenderAndDecreaseBorrowerDebt(lender, delay, _money);

                        unblock(debt, lender);
                        return _money.minus(debt);
                    })
                    .orElse(money);
        }
        return money;
    }

    private Money unblockFromLenderAndDecreaseBorrowerDebt(SettlementDelay lender, SettlementDelay borrower, Money _toUnblock) {
        delayWallets.get(lender).unblock(_toUnblock);
        debtSupervisor.decrease(lender, borrower, _toUnblock);
        return Money.ZERO;
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
        if (borrower.value() != lender.value())
            debtSupervisor.increase(lender, borrower, debt);
    }

    private boolean internalWalletBuyingPowerIsNotZero(SettlementDelay delay) {
        return delayWallets.get(delay).buyingPower().isGreaterThan(Money.ZERO);
    }

    private void throwIfToSpendIsMoreThanWalletBlockedMoney(Money toSpend, SettlementDelay delay) {
        var blocked = Money.of(delayWallets.get(delay).getBlocked().value());
        var totalPossibleToSpend = delay
                .lessThan()
                .map(d -> debtSupervisor.get(d.asLender(), delay.asBorrower()))
                .fold(blocked, (acc, money) -> acc.plus(money == null ? Money.ZERO : money));
        if (toSpend.isGreaterThan(totalPossibleToSpend))
            throw new InsufficientFundsException(String.format("Required %s for spending but have only %s", toSpend.value(), totalPossibleToSpend.value()));
    }

    private OptionalDebt borrowerToLenderPossibleDebt(SettlementDelay walletDelay, SettlementDelay lender) {
        return new OptionalDebt(debtSupervisor.get(lender.asLender(), walletDelay.asBorrower()));
    }

    static class DebtSupervisor {
        private final Table<Lender, Borrower, Money> higherWalletsToLowerWalletsDebt;


        public DebtSupervisor() {
            higherWalletsToLowerWalletsDebt = HashBasedTable.create(4, 4);
        }

        public void increase(Lender lender, Borrower borrower, Money amount) {
            higherWalletsToLowerWalletsDebt.put(lender, borrower, get(lender, borrower).plus(amount));
        }

        public void clear(SettlementDelay lender, SettlementDelay borrower) {
            higherWalletsToLowerWalletsDebt.put(lender.asLender(), borrower.asBorrower(), Money.ZERO);
        }

        public void decrease(SettlementDelay lender, SettlementDelay borrower, Money amount) {
            // TODO: 11/23/2022 Can check if amount is not greater than debt
            higherWalletsToLowerWalletsDebt.put(
                    lender.asLender(),
                    borrower.asBorrower(),
                    get(lender.asLender(), borrower.asBorrower()).minus(amount)
            );
        }

        public Money get(Lender lender, Borrower borrower) {
            var debt = higherWalletsToLowerWalletsDebt.get(lender, borrower);
            if (debt == null)
                debt = Money.ZERO;
            return debt;
        }
    }
}

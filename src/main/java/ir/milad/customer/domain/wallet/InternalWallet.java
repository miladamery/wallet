package ir.milad.customer.domain.wallet;

import lombok.Getter;

class InternalWallet {
    private Balance cash;
    @Getter
    private Balance blocked;

    public InternalWallet() {
        cash = new Balance(0L);
        blocked = new Balance(0L);
    }

    public void block(Money block) {
        if (cash.isLessThan(block))
            throw new InsufficientFundsException(String.format("Required %s for withdraw but had %s", block.value(), cash.value()));

        cash = cash.minus(block);
        blocked = blocked.plus(block);
    }

    public void deposit(Money deposit) {
        cash = cash.plus(deposit);
    }

    public void withdraw(Money withdraw) {
        if (cash.isLessThan(withdraw))
            throw new InsufficientFundsException(String.format("Required %s for withdraw but had %s", withdraw.value(), cash.value()));

        cash = cash.minus(withdraw);
    }

    public void spend(Money spend) {
        if (blocked.isLessThan(spend))
            throw new InsufficientFundsException(String.format("Required %s for spending but had %s", spend.value(), blocked.value()));

        blocked = blocked.minus(spend);
    }

    public void unblock(Money unblock) {
        if (blocked.isLessThan(unblock))
            throw new InsufficientFundsException(String.format("Required %s for unblock but had %s", unblock.value(), blocked.value()));

        blocked = blocked.minus(unblock);
        cash = cash.plus(unblock);
    }

    public Balance buyingPower() {
        return cash;
    }
}

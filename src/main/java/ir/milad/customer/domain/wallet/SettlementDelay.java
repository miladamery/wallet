package ir.milad.customer.domain.wallet;

import io.vavr.collection.List;

public enum SettlementDelay {
    T_PLUS_0 {
        @Override
        public List<SettlementDelay> EqualAndLess() {
            return List.of(T_PLUS_0);
        }

        @Override
        public List<SettlementDelay> lessThan() {
            return List.empty();
        }

        @Override
        public List<SettlementDelay> lessThanAndEqual() {
            return List.of(T_PLUS_0);
        }

        @Override
        public Lender asLender() {
            return Lender.of(this);
        }

        @Override
        public Borrower asBorrower() {
            return Borrower.of(this);
        }
    },
    T_PLUS_1 {
        @Override
        public List<SettlementDelay> EqualAndLess() {
            return List.of(T_PLUS_1, T_PLUS_0);
        }

        @Override
        public List<SettlementDelay> lessThan() {
            return List.of(T_PLUS_0);
        }

        @Override
        public List<SettlementDelay> lessThanAndEqual() {
            return List.of(T_PLUS_0, T_PLUS_1);
        }

        @Override
        public Lender asLender() {
            return Lender.of(this);
        }

        @Override
        public Borrower asBorrower() {
            return Borrower.of(this);
        }
    },
    T_PLUS_2 {
        @Override
        public List<SettlementDelay> EqualAndLess() {
            return List.of(T_PLUS_2, T_PLUS_1, T_PLUS_0);
        }

        @Override
        public List<SettlementDelay> lessThan() {
            return List.of(T_PLUS_0, T_PLUS_1);
        }

        @Override
        public List<SettlementDelay> lessThanAndEqual() {
            return List.of(T_PLUS_0, T_PLUS_1, T_PLUS_2);
        }

        @Override
        public Lender asLender() {
            return Lender.of(this);
        }

        @Override
        public Borrower asBorrower() {
            return Borrower.of(this);
        }
    },
    T_PLUS_3 {
        @Override
        public List<SettlementDelay> EqualAndLess() {
            return List.of(T_PLUS_3, T_PLUS_2, T_PLUS_1, T_PLUS_0);
        }

        @Override
        public List<SettlementDelay> lessThan() {
            return List.of(T_PLUS_0, T_PLUS_1, T_PLUS_2);
        }

        @Override
        public List<SettlementDelay> lessThanAndEqual() {
            return List.of(T_PLUS_0, T_PLUS_1, T_PLUS_2, T_PLUS_3 );
        }

        @Override
        public Lender asLender() {
            return Lender.of(this);
        }

        @Override
        public Borrower asBorrower() {
            return Borrower.of(this);
        }
    };

    public abstract List<SettlementDelay> EqualAndLess();

    public abstract List<SettlementDelay> lessThan();

    public abstract List<SettlementDelay> lessThanAndEqual();

    public abstract Lender asLender();
    
    public abstract Borrower asBorrower();
}

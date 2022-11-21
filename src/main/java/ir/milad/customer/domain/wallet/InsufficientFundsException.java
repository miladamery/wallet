package ir.milad.customer.domain.wallet;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException() {
    }

    public InsufficientFundsException(String message) {
        super(message);
    }
}

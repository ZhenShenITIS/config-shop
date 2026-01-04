package tg.configshop.exceptions;

public class CyclicReferralException extends RuntimeException {
    public CyclicReferralException(String message) {
        super(message);
    }

    public CyclicReferralException() {
    }
}

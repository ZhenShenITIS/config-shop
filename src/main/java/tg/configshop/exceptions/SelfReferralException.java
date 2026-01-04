package tg.configshop.exceptions;

public class SelfReferralException extends RuntimeException {
    public SelfReferralException(String message) {
        super(message);
    }

    public SelfReferralException() {
    }
}

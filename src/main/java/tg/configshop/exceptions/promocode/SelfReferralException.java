package tg.configshop.exceptions.promocode;

public class SelfReferralException extends RuntimeException {
    public SelfReferralException(String message) {
        super(message);
    }

    public SelfReferralException() {
    }
}

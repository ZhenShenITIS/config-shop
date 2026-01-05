package tg.configshop.exceptions.promocode;

public class CyclicReferralException extends RuntimeException {
    public CyclicReferralException(String message) {
        super(message);
    }

    public CyclicReferralException() {
    }
}

package tg.configshop.exceptions.promocode;

public class TooManyReferralPromoException extends RuntimeException {
    public TooManyReferralPromoException(String message) {
        super(message);
    }

    public TooManyReferralPromoException() {
    }
}

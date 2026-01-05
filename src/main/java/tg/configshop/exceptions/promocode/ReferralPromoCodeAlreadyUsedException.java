package tg.configshop.exceptions.promocode;

public class ReferralPromoCodeAlreadyUsedException extends RuntimeException {
    public ReferralPromoCodeAlreadyUsedException(String message) {
        super(message);
    }

    public ReferralPromoCodeAlreadyUsedException() {
    }
}

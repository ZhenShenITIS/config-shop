package tg.configshop.exceptions;

public class ReferralPromoCodeAlreadyUsedException extends RuntimeException {
    public ReferralPromoCodeAlreadyUsedException(String message) {
        super(message);
    }

    public ReferralPromoCodeAlreadyUsedException() {
    }
}

package tg.configshop.exceptions;

public class PromoCodeAlreadyUsedException extends RuntimeException {
    public PromoCodeAlreadyUsedException(String message) {
        super(message);
    }

    public PromoCodeAlreadyUsedException() {
    }
}

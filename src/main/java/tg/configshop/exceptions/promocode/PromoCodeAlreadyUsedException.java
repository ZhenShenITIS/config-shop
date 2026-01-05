package tg.configshop.exceptions.promocode;

public class PromoCodeAlreadyUsedException extends RuntimeException {
    public PromoCodeAlreadyUsedException(String message) {
        super(message);
    }

    public PromoCodeAlreadyUsedException() {
    }
}

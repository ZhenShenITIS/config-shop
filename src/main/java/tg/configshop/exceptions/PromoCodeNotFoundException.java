package tg.configshop.exceptions;

public class PromoCodeNotFoundException extends RuntimeException {
    public PromoCodeNotFoundException(String message) {
        super(message);
    }

    public PromoCodeNotFoundException() {
    }
}

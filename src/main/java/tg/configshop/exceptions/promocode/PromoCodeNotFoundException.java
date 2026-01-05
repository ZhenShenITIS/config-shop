package tg.configshop.exceptions.promocode;

public class PromoCodeNotFoundException extends RuntimeException {
    public PromoCodeNotFoundException(String message) {
        super(message);
    }

    public PromoCodeNotFoundException() {
    }
}

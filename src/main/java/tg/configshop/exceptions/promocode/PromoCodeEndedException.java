package tg.configshop.exceptions.promocode;

public class PromoCodeEndedException extends RuntimeException {
    public PromoCodeEndedException(String message) {
        super(message);
    }

    public PromoCodeEndedException() {
    }
}

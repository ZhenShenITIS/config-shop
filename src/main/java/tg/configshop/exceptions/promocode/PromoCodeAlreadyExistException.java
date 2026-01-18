package tg.configshop.exceptions.promocode;

public class PromoCodeAlreadyExistException extends RuntimeException {
    public PromoCodeAlreadyExistException(String message) {
        super(message);
    }

    public PromoCodeAlreadyExistException() {
    }
}

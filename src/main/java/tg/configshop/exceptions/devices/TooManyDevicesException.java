package tg.configshop.exceptions.devices;

public class TooManyDevicesException extends RuntimeException {
    public TooManyDevicesException(String message) {
        super(message);
    }

    public TooManyDevicesException() {
    }
}

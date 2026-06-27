package tg.configshop.exceptions.traffic;

public class TrafficPackageNotFoundException extends RuntimeException {
    public TrafficPackageNotFoundException() {
        super("Traffic package not found");
    }
}

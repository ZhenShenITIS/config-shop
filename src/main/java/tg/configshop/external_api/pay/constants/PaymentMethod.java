package tg.configshop.external_api.pay.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public enum PaymentMethod {
    SBP("SBPQR", 2),
    CARD_RU("CardRu", 10),
    CARD("Card", 11),
    INTERNATIONAL("International", 12),
    CRYPTO("Crypto", 13);

    private final String textMethod;
    private final int intMethod;

    private static final Map<String, PaymentMethod> TEXT_MAP = new HashMap<>();
    private static final Map<Integer, PaymentMethod> INT_MAP = new HashMap<>();

    static {
        for (PaymentMethod pm : values()) {
            TEXT_MAP.put(pm.textMethod, pm);
            INT_MAP.put(pm.intMethod, pm);
        }
    }
    public static PaymentMethod fromTextMethod(String textMethod) {
        return Optional.ofNullable(TEXT_MAP.get(textMethod))
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + textMethod));
    }

    public static PaymentMethod fromIntMethod(int intMethod) {
        return Optional.ofNullable(INT_MAP.get(intMethod))
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + intMethod));
    }

    @JsonCreator
    public static PaymentMethod fromJson(String value) {
        return fromTextMethod(value);
    }

    @JsonValue
    public String toJson() {
        return this.textMethod;
    }
}

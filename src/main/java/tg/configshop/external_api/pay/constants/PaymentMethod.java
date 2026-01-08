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

    private static final Map<String, PaymentMethod> LOOKUP_MAP = new HashMap<>();

    static {
        for (PaymentMethod pm : values()) {
            LOOKUP_MAP.put(pm.textMethod, pm);
        }
    }
    public static PaymentMethod fromTextMethod(String textMethod) {
        return Optional.ofNullable(LOOKUP_MAP.get(textMethod))
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment method: " + textMethod));
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

package tg.configshop.telegram.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CallbackName {
    NONE("none");

    private final String callbackName;

}

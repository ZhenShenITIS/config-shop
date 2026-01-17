package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DialogStageName {
    PROMO_CODE_INPUT("promo_input"),
    PAYMENT("payment"),
    CRYPTO_WITHDRAW_SUM("crypto_wd_sum"),
    DEVICE_INPUT("device_input"),
    NONE("none");

    private final String dialogStageName;
}

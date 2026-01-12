package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DialogStageName {
    PROMO_CODE_INPUT("promo_input"),
    SBP_PAY("sbp_pay"),
    CRYPTO_WITHDRAW_SUM("crypto_wd_sum"),
    NONE("none");

    private final String dialogStageName;
}

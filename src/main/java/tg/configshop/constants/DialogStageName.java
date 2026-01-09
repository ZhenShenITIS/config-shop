package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DialogStageName {
    PROMO_CODE_INPUT("promo_input"),
    SBP_PAY("sbp_pay"),
    NONE("none");

    private final String dialogStageName;
}

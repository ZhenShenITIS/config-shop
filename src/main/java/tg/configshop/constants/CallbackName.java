package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CallbackName {
    NONE("none"),
    // start-menu
    BALANCE("balance"),
    SUBSCRIPTION("subscription"),
    PROMO_CODE("promo_code"),
    REFERRAL("referral"),

    // balance
    HISTORY("history"),
    TOP_UP("top_up"),

    // referral
    REF_LIST("ref_list"),
    REF_ANALYTICS("ref_analytics"),


    BACK_TO_MENU("back1");

    private final String callbackName;

}

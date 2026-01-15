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

    BUY_SUB_MENU("buy_sub_menu"),

    WITHDRAW("withdraw"),

    BUY_PERIOD_30("buy_period_30"),
    BUY_PERIOD_90("buy_period_90"),
    BUY_PERIOD_180("buy_period_180"),
    BUY_PERIOD_360("buy_period_360"),

    CONFIRM_BUY("confirm_buy"),

    PURCHASE("pay"),

    DOCS_CONFIRM("docs_confirm"),

    DOCS_INFO("docs_info"),

    DOCS_DECLINE("docs_decline"),
    PAYMENT_SBP("payment_sbp"),
    PAYMENT_CRYPTO("payment_crypto"),

    CHECK_STATUS_SBP("check_sbp"),

    CRYPTO_WITHDRAWAL("crpt_with"),

    DEVICES("devices"),
    DELETE_DEVICES("delete_devices"),
    BUY_MORE_DEVICES("buy_more_devices"),
    CONFIRM_DEVICE_PURCHASE("confirm_dev_pur"),
    PREVIEW_DEVICE_PURCHASE("preview_device_purchase"),
    CANCEL_DEVICE_PURCHASE("cancel_device_purchase"),

    BACK_TO_MENU("back");

    private final String callbackName;

}

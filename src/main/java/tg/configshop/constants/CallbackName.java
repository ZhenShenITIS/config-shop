package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CallbackName {
    NONE("0"),
    // start-menu
    BALANCE("1"),
    SUBSCRIPTION("2"),
    PROMO_CODE("3"),
    REFERRAL("4"),

    // balance
    HISTORY("5"),
    TOP_UP("6"),

    // referral
    REF_LIST("7"),
    REF_ANALYTICS("8"),

    BUY_SUB_MENU("9"),

    WITHDRAW("a"),

    BUY_PERIOD_30("b"),
    BUY_PERIOD_90("c"),
    BUY_PERIOD_180("d"),
    BUY_PERIOD_360("e"),

    CONFIRM_BUY("f"),

    PURCHASE("g"),

    DOCS_CONFIRM("h"),

    DOCS_INFO("k"),

    DOCS_DECLINE("l"),
    PAYMENT_INPUT_SUM("m"),
    PAYMENT_CRYPTO("n"),

    CHECK_STATUS_PAYMENT("o"),

    CRYPTO_WITHDRAWAL("p"),

    DEVICES("q"),
    DELETE_DEVICES("r"),
    BUY_MORE_DEVICES("s"),
    CONFIRM_DEVICE_PURCHASE("t"),
    PREVIEW_DEVICE_PURCHASE("j"),
    CANCEL_DEVICE_PURCHASE("x"),
    CONFIRM_WITHDRAWAL("w1"),
    ADMIN_APPROVE_WD("adm_w_ok"),
    ADMIN_REJECT_WD("adm_w_no"),
    ADMIN_REFRESH_WD("adm_w_upd"),

    ADD_REF_PROMO("z"),

    BACK_TO_MENU("y");

    private final String callbackName;

}

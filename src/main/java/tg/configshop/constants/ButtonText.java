package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ButtonText {
    BALANCE("💸 Баланс: %s ₽"),
    PROMO_CODE("\uD83D\uDDDD Промокод"),//🗝️
    REFERRAL("👥 Рефералы"),
    SUPPORT("👨‍🔧 Поддержка"),
    SUBSCRIPTION("\uD83D\uDCC4 Подписка"), // 📄
    CONNECT("🔗 Подключиться"),
    INSTRUCTION("📑 Инструкция"),
    HISTORY("📜 История операций"),
    TOP_UP("💳 Пополнить"),
    BACK("🔙 Назад"),
    REF_LIST("👥 Список рефералов"),
    REF_ANALYTICS("📈 Аналитика"),
    WITHDRAW("📤 Вывод"),
    BUY_SUB("💳 Продлить / Купить"),
    DEVICES("📱 Устройства"),
    SUB_PERIOD_1_MONTH("📅 1 месяц — %d ₽"),
    SUB_PERIOD_3_MONTH("📅 3 месяца — %d ₽"),
    SUB_PERIOD_6_MONTH("📅 6 месяцев — %d ₽"),
    SUB_PERIOD_12_MONTH("📅 1 год — %d ₽"),
    DEVICE_OPTION_SELECTED("✅ %d (%d ₽)"),
    DEVICE_OPTION_UNSELECTED("🔘 %d (%d ₽)"),
    CONFIRM_PAYMENT("✅ Оплатить %d ₽"),
    CONFIRM_BUY("✅ Подтвердить"),
    INFO("ℹ️Инфо"),
    POLICY("Политика"),
    AGREEMENT("Соглашение"),
    BACK_PAGE("⬅️"),
    FORWARD_PAGE("➡️"),
    EMPTY("⠀"),
    ACCEPT_RULES("✅ Принимаю"),
    DECLINE_RULES("❌ Отказаться"),
    PAYMENT_METHOD_SBP("💠 СБП"),
    PAYMENT_METHOD_CARD("💳 Карта"),
    PAYMENT_METHOD_CRYPTO("💎 Криптовалюта"),
    PAY_ACTION("💳 Оплатить"),
    CHECK_PAYMENT("🔄 Проверить оплату"),
    DELETE_DEVICES("🗑️ Удалить устройства"),
    BUY_MORE_DEVICES("➕ Докупить устройства"),
    BACK_TO_MENU("🔙 В главное меню");

    private final String text;
}

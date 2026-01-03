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
    HISTORY("📜 История операций"),
    TOP_UP("💳 Пополнить"),
    BACK("🔙 Назад"),
    REF_LIST("👥 Список рефералов"),
    REF_ANALYTICS("📈 Аналитика");

    private final String text;
}

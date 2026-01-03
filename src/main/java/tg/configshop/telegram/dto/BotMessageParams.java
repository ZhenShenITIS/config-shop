package tg.configshop.telegram.dto;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public record BotMessageParams(
        String text,
        InlineKeyboardMarkup inlineKeyboard
) {
}

package tg.configshop.util;

import org.telegram.telegrambots.meta.api.objects.message.Message;

public class MessageUtils {
    public static Long getReferrerId(Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length > 1) {
            String payload = parts[1];
            try {
                return Long.parseLong(payload);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

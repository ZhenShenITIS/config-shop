package tg.configshop.util;

import tg.configshop.model.BotUser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    public static boolean isExpired (BotUser botUser) {
        return botUser.getExpireAt().isBefore(Instant.now());
    }
    public static String getDateEndSubscription (BotUser botUser) {
        Instant date = botUser.getExpireAt();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        ZonedDateTime zdt = date.atZone(ZoneId.of("Europe/Moscow"));
        return zdt.format(formatter);
    }
}

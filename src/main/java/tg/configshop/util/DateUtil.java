package tg.configshop.util;

import tg.configshop.model.BotUser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateUtil {
    private final static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static boolean isExpired (BotUser botUser) {
        return botUser.getExpireAt().isBefore(Instant.now());
    }

    public static String getDateEndSubscription (BotUser botUser) {
        Instant date = botUser.getExpireAt();
        ZonedDateTime zdt = date.atZone(ZoneId.of("Europe/Moscow"));
        return zdt.format(dateTimeFormatter);
    }

    public static long getDaysLeft(BotUser botUser) {
        if (isExpired(botUser)) return 0;
        return java.time.Duration.between(Instant.now(), botUser.getExpireAt()).toDays();
    }

    public static String getPrettyDate (Instant date) {
        ZonedDateTime zdt = date.atZone(ZoneId.of("Europe/Moscow"));
        return zdt.format(dateFormatter);
    }

    public static String getPrettyDateWithTime (Instant date) {
        if (date == null) return "";
        ZonedDateTime zdt = date.atZone(ZoneId.of("Europe/Moscow"));
        return zdt.format(dateTimeFormatter);
    }
}

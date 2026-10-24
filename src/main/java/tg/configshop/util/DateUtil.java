package tg.configshop.util;


import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    private final static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static boolean isExpired (Instant expireAt) {
        return expireAt.isBefore(Instant.now());
    }

    public static String getDateEndSubscription (Instant expireAt) {
        Instant date = expireAt;
        ZonedDateTime zdt = date.atZone(ZoneId.of("Europe/Moscow"));
        return zdt.format(dateTimeFormatter);
    }

    public static long getDaysLeft(Instant expireAt) {
        if (isExpired(expireAt)) return 0;
        return java.time.Duration.between(Instant.now(), expireAt).toDays();
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

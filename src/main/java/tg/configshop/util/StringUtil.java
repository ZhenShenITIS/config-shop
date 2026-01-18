package tg.configshop.util;

import tg.configshop.exceptions.promocode.InvalidSymbolsPromoException;

import java.util.regex.Pattern;

public class StringUtil {

    private static final String PROMO_PATTERN_STRING = "^[a-zA-Z0-9_-]{3,20}$";
    private static final Pattern PROMO_PATTERN = Pattern.compile(PROMO_PATTERN_STRING);


    public static String getSafeHtmlString (String unsafe) {
        return unsafe
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static void validateCodeFormat(String code) {
        if (code == null || !PROMO_PATTERN.matcher(code).matches()) {
            throw new InvalidSymbolsPromoException();
        }
    }
}

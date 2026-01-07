package tg.configshop.util;

public class StringUtil {
    public static String getSafeHtmlString (String unsafe) {
        return unsafe
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

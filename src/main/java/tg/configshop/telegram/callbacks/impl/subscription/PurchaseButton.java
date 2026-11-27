package tg.configshop.telegram.callbacks.impl.subscription;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

final class PurchaseButton {
    private PurchaseButton() {}

    static String encode(UUID id) {
        var bytes = ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array());
    }

    static UUID decode(String value) {
        var bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length != 16) throw new IllegalArgumentException("Invalid purchase button ID");
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}

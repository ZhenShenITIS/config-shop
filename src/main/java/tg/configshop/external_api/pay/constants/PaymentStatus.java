package tg.configshop.external_api.pay.constants;

public enum PaymentStatus {
    PENDING, CANCELED, CONFIRMED, CHARGEBACKED, PARTIALLY_REFUNDED, DISPUTED,
    CREATING, CREATION_UNCERTAIN, CREATION_FAILED;

    public boolean isCreationState() {
        return this == CREATING || this == CREATION_UNCERTAIN || this == CREATION_FAILED;
    }

    public boolean requiresReview() {
        return this == CHARGEBACKED || this == PARTIALLY_REFUNDED || this == DISPUTED;
    }
}

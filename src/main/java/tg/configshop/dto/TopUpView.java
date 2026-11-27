package tg.configshop.dto;

import java.util.UUID;

public record TopUpView(UUID id, long amount, State state, String redirect) {
    public enum State {
        CREATING, PENDING, CONFIRMED, CANCELED, FAILED, MANUAL_REVIEW
    }
}

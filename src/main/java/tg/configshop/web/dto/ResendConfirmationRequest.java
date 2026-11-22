package tg.configshop.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ResendConfirmationRequest(@NotBlank @Email @Size(max = 320) String email) {
    public ResendConfirmationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

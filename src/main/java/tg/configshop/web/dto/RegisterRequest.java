package tg.configshop.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Size(max = 255) String referralCode) {
    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        referralCode = referralCode == null ? null : referralCode.trim();
    }
}

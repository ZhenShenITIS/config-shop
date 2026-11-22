package tg.configshop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import tg.configshop.web.WebTokens;

public record ConfirmEmailRequest(@NotBlank @Pattern(regexp = WebTokens.FORMAT) String token) {
}

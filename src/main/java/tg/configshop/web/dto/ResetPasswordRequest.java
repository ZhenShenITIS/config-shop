package tg.configshop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tg.configshop.web.WebTokens;

public record ResetPasswordRequest(@NotBlank @Pattern(regexp = WebTokens.FORMAT) String token,
                                   @NotBlank @Size(min = 12, max = 72) String password) { }

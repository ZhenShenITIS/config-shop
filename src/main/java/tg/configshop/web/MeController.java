package tg.configshop.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.web.dto.MeResponse;
import tg.configshop.web.security.WebPrincipal;

@RestController
public class MeController {
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal WebPrincipal user) {
        return new MeResponse(user.webUserId(), user.accountId(), user.email(), user.emailVerifiedAt());
    }
}

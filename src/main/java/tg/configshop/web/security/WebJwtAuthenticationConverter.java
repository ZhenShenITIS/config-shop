package tg.configshop.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.repositories.WebUserRepository;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final WebUserRepository users;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var user = users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new InvalidBearerTokenException("Web user does not exist"));
        if (!user.getAccountId().equals(UUID.fromString(jwt.getClaimAsString("account_id")))) {
            throw new InvalidBearerTokenException("Account does not match web user");
        }
        var principal = new WebPrincipal(user.getId(), user.getAccountId(), user.getEmail(), user.getEmailVerifiedAt());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}

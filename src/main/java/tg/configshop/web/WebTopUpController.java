package tg.configshop.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.dto.TopUpView;
import tg.configshop.external_api.pay.constants.PaymentMethod;
import tg.configshop.services.PaymentService;
import tg.configshop.web.registration.WebAuthException;
import tg.configshop.web.security.WebPrincipal;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/top-ups")
public class WebTopUpController {
    private final PaymentService payments;
    private final URI returnPage;
    private final List<Method> methods;

    public WebTopUpController(PaymentService payments,
                              @Value("${web.payments.return-url}") URI returnPage,
                              @Value("${web.payments.methods}") List<Integer> methodCodes) {
        if (!"https".equalsIgnoreCase(returnPage.getScheme()) || returnPage.getHost() == null
                || returnPage.getQuery() != null || returnPage.getFragment() != null || returnPage.getUserInfo() != null) {
            throw new IllegalArgumentException("WEB_PAYMENT_RETURN_URL must be an absolute HTTPS page URL");
        }
        this.payments = payments;
        this.returnPage = returnPage;
        this.methods = methodCodes.stream().distinct().map(PaymentMethod::fromIntMethod)
                .map(method -> new Method(method.getIntMethod(), switch (method) {
                    case SBP -> "СБП";
                    case CARD, CARD_RU -> "Банковская карта";
                    case INTERNATIONAL -> "Международная карта";
                    case CRYPTO -> "Криптовалюта";
                })).toList();
        if (methods.isEmpty()) throw new IllegalArgumentException("Configure at least one web payment method");
    }

    @GetMapping("/options")
    public Options options() {
        var limits = payments.limits();
        int defaultMethod = methods.stream().mapToInt(Method::code)
                .filter(code -> code == PaymentMethod.SBP.getIntMethod()).findFirst().orElse(methods.getFirst().code());
        return new Options(limits.minimum(), limits.maximum(), List.of(100, 300, 500, 1000), methods, defaultMethod);
    }

    @PostMapping
    public TopUpView create(@AuthenticationPrincipal WebPrincipal user, @Valid @RequestBody CreateRequest request) {
        var limits = payments.limits();
        long amount;
        int paymentMethod;
        try {
            amount = request.amount().longValueExact();
            paymentMethod = request.paymentMethod().intValueExact();
        } catch (ArithmeticException e) {
            throw invalidTopUp();
        }
        if (amount < limits.minimum() || amount > limits.maximum()
                || methods.stream().noneMatch(method -> method.code() == paymentMethod)) {
            throw invalidTopUp();
        }
        String url = returnPage.toASCIIString() + "?topUp=" + request.id();
        try {
            payments.createTopUp(request.id(), amount, paymentMethod, user.accountId(),
                    user.email(), url, url);
        } catch (IllegalArgumentException e) {
            throw new WebAuthException(HttpStatus.CONFLICT, "top_up_conflict",
                    "Эта заявка недоступна для выбранной суммы или способа оплаты.");
        }
        return status(user, request.id());
    }

    @GetMapping("/{id}")
    public TopUpView status(@AuthenticationPrincipal WebPrincipal user, @PathVariable UUID id) {
        return payments.getTopUp(id, user.accountId())
                .orElseThrow(() -> new WebAuthException(HttpStatus.NOT_FOUND, "top_up_not_found", "Платёж не найден."));
    }

    private static WebAuthException invalidTopUp() {
        return new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_top_up", "Проверьте сумму и способ оплаты.");
    }

    public record CreateRequest(@NotNull UUID id, @NotNull BigDecimal amount, @NotNull BigDecimal paymentMethod) {}
    public record Method(int code, String label) {}
    public record Options(long minimum, long maximum, List<Integer> presets, List<Method> methods, int defaultMethod) {}
}

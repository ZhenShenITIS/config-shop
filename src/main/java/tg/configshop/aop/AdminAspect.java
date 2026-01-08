package tg.configshop.aop;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import tg.configshop.repositories.AdministratorRepository;

@Aspect
@Component
@RequiredArgsConstructor
public class AdminAspect {

    private final AdministratorRepository administratorRepository;

    @Around("@annotation(adminOnly)")
    public Object validate (ProceedingJoinPoint joinPoint, AdminOnly adminOnly) throws Throwable {
        Object[] args = joinPoint.getArgs();

        if (args[0] instanceof CallbackQuery callbackQuery) {
            if (administratorRepository.isAdmin(callbackQuery.getFrom().getId())) {
                return joinPoint.proceed();
            }
        } else if (args[0] instanceof Message message) {
            if (administratorRepository.isAdmin(message.getFrom().getId())) {
                return joinPoint.proceed();
            }
        }
        return null;
    }
}

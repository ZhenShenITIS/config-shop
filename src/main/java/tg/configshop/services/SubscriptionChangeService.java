package tg.configshop.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tg.configshop.constants.PurchaseType;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.dto.SubscriptionChangeResult;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.exceptions.subscription.SubscriptionChangeInProgressException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.Purchase;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.model.SubscriptionChange;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.repositories.SubscriptionChangeRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.ToLongBiFunction;

import static tg.configshop.constants.SubscriptionChangeStatus.*;

@Slf4j
@Service
public class SubscriptionChangeService {
    private static final int MAX_ATTEMPTS = 8;
    private final ReentrantLock[] locks = new ReentrantLock[64];
    private final TransactionTemplate transaction;
    private final SubscriptionChangeRepository changes;
    private final SubscriptionAccessService subscriptions;
    private final AccountRepository accounts;
    private final BalanceService balance;
    private final RemnawaveClient panel;
    private final RemnawaveApiVersion apiVersion;
    private final SchedulerService reminders;

    public SubscriptionChangeService(PlatformTransactionManager transactions, SubscriptionChangeRepository changes,
                                     SubscriptionAccessService subscriptions, AccountRepository accounts,
                                     BalanceService balance, RemnawaveClient panel, RemnawaveApiVersion apiVersion,
                                     SchedulerService reminders) {
        this.transaction = new TransactionTemplate(transactions);
        this.changes = changes;
        this.subscriptions = subscriptions;
        this.accounts = accounts;
        this.balance = balance;
        this.panel = panel;
        this.apiVersion = apiVersion;
        this.reminders = reminders;
        Arrays.setAll(locks, ignored -> new ReentrantLock());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubscriptionChangeResult renew(UUID id, UUID accountId, UUID subscriptionId, int days, int devices, LongSupplier price) {
        var lock = lock(subscriptionId);
        lock.lock();
        try {
            apiVersion.select(subscriptions.requireOwned(accountId, subscriptionId).remnawaveRef());
            var change = transaction.execute(tx -> {
                lockOwner(accountId, subscriptionId);
                var existing = changes.findById(id).orElse(null);
                if (existing != null) {
                    if (!existing.getSubscriptionId().equals(subscriptionId) || existing.getDays() != days
                            || existing.getDevices() != devices) {
                        throw new IllegalArgumentException("Subscription purchase parameters changed");
                    }
                    return existing;
                }
                requireNoActiveChange(subscriptionId);
                return changes.save(new SubscriptionChange(id, subscriptionId, days, devices, price.getAsLong()));
            });
            return result(advance(change));
        } finally {
            lock.unlock();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubscriptionChangeResult buyDevices(UUID accountId, UUID subscriptionId, int devices,
                                               ToLongBiFunction<Instant, Integer> quote) {
        var lock = lock(subscriptionId);
        lock.lock();
        try {
            var owned = subscriptions.requireOwned(accountId, subscriptionId);
            apiVersion.select(owned.remnawaveRef());
            requireNoActiveChange(subscriptionId);
            var current = readPanel(owned);
            long price = quote.applyAsLong(current.expireAt(), deviceLimit(current));
            var change = transaction.execute(tx -> {
                var subscription = lockOwner(accountId, subscriptionId);
                requireNoActiveChange(subscriptionId);
                var pending = new SubscriptionChange(UUID.randomUUID(), subscriptionId, 0, devices, price);
                return debit(pending, subscription, current, current.expireAt(), Math.addExact(deviceLimit(current), devices));
            });
            return result(advance(change));
        } finally {
            lock.unlock();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void retry(UUID id, UUID subscriptionId) {
        var lock = lock(subscriptionId);
        if (!lock.tryLock()) return;
        try {
            changes.findById(id).ifPresent(this::advance);
        } finally {
            lock.unlock();
        }
    }

    private SubscriptionChange advance(SubscriptionChange change) {
        if (!RETRYABLE.contains(change.getStatus()) || change.getNextAttemptAt().isAfter(Instant.now())) return change;
        try {
            var subscription = subscriptions.requireExisting(change.getSubscriptionId());
            boolean preparedNow = change.getStatus() == PREPARING;
            if (preparedNow) {
                var current = readPanel(subscription);
                change = prepareRenewal(change, current);
            }
            if (change.getStatus() == APPLYING) {
                change = apply(change, subscription, preparedNow);
            }
            if (change.getStatus() == APPLIED) {
                reminders.rescheduleSubscriptionNotifications(subscription.getAccount().getId(), subscription.getId(),
                        change.getTargetExpiry());
                change = setStatus(change.getId(), COMPLETED);
            }
            return change;
        } catch (RuntimeException exception) {
            log.warn("Subscription change {} is pending after {}", change.getId(), exception.getClass().getSimpleName());
            return recordFailure(change.getId(), exception.getClass().getSimpleName());
        }
    }

    private SubscriptionChange prepareRenewal(SubscriptionChange change, RemnawaveUserResponse current) {
        try {
            return transaction.execute(tx -> {
                var stored = changes.findById(change.getId()).orElseThrow();
                // The subscription is the owner of the operation even after its account is merged.
                var owner = subscriptions.requireExisting(stored.getSubscriptionId()).getAccount().getId();
                var subscription = lockOwner(owner, stored.getSubscriptionId());
                Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
                var expiry = current.expireAt().isAfter(now) ? current.expireAt() : now;
                return debit(stored, subscription, current, expiry.plus(stored.getDays(), ChronoUnit.DAYS),
                        stored.getDevices());
            });
        } catch (InsufficientBalanceException e) {
            return setStatus(change.getId(), INSUFFICIENT_BALANCE);
        }
    }

    private SubscriptionChange debit(SubscriptionChange change, RemnawaveSubscription subscription,
                                     RemnawaveUserResponse current, Instant targetExpiry, int targetDevices) {
        var builder = Purchase.builder().account(subscription.getAccount()).remnawaveSubscription(subscription)
                .paidAmount(change.getPrice());
        var purchase = change.isRenewal()
                ? builder.subDays(change.getDays()).subDevices(change.getDevices()).build()
                : builder.purchaseType(PurchaseType.DEVICE).deviceCount(change.getDevices()).build();
        balance.purchase(purchase);
        // The ledger write flushes JPA. Set all fields of the paid operation together after it.
        change.setPreviousExpiry(current.expireAt().truncatedTo(ChronoUnit.MILLIS));
        change.setPreviousDevices(deviceLimit(current));
        change.setTargetExpiry(targetExpiry.truncatedTo(ChronoUnit.MILLIS));
        change.setTargetDevices(targetDevices);
        change.setPurchaseId(purchase.getId());
        change.setStatus(APPLYING);
        change.setNextAttemptAt(Instant.now());
        change.setAttempts(0);
        change.setLastError(null);
        return changes.save(change);
    }

    private SubscriptionChange apply(SubscriptionChange change, RemnawaveSubscription subscription, boolean preparedNow) {
        if (!preparedNow) {
            var current = readPanel(subscription);
            if (matches(current, change.getTargetExpiry(), change.getTargetDevices())) {
                return setStatus(change.getId(), change.isRenewal() ? APPLIED : COMPLETED);
            }
            if (!matches(current, change.getPreviousExpiry(), change.getPreviousDevices())) {
                log.error("Remnawave state conflicts with subscription change {}", change.getId());
                return setStatus(change.getId(), MANUAL_REVIEW, "Remnawave state conflicts with recorded targets");
            }
        }

        if (change.isRenewal()) {
            var updated = panel.updateSubscription(subscription.remnawaveRef(), change.getTargetExpiry(), change.getTargetDevices());
            if (!matches(updated, change.getTargetExpiry(), change.getTargetDevices())) {
                throw new IllegalStateException("Remnawave did not confirm the requested change");
            }
            return setStatus(change.getId(), APPLIED);
        }
        panel.updateDeviceCount(subscription.remnawaveRef(), change.getTargetDevices());
        return setStatus(change.getId(), COMPLETED);
    }

    private RemnawaveUserResponse readPanel(RemnawaveSubscription subscription) {
        var current = panel.getUser(subscription.remnawaveRef());
        if (current == null || current.expireAt() == null
                || (subscription.getRemnawaveId() != null && current.id() != null
                    && !Objects.equals(subscription.getRemnawaveId(), current.id()))) {
            throw new IllegalStateException("Incomplete or conflicting Remnawave subscription state");
        }
        return current;
    }

    private static boolean matches(RemnawaveUserResponse current, Instant expiry, int devices) {
        return current != null && current.expireAt() != null
                && current.expireAt().truncatedTo(ChronoUnit.MILLIS).equals(expiry.truncatedTo(ChronoUnit.MILLIS))
                && deviceLimit(current) == devices;
    }

    private static int deviceLimit(RemnawaveUserResponse current) {
        return current.hwidDeviceLimit() == null ? 0 : current.hwidDeviceLimit();
    }

    private RemnawaveSubscription lockOwner(UUID accountId, UUID subscriptionId) {
        accounts.findByIdWithLock(accountId).orElseThrow(() -> new IllegalArgumentException("Account no longer exists"));
        return subscriptions.requireOwned(accountId, subscriptionId);
    }

    private void requireNoActiveChange(UUID subscriptionId) {
        if (changes.existsBySubscriptionIdAndStatusIn(subscriptionId, ACTIVE)) {
            throw new SubscriptionChangeInProgressException();
        }
    }

    private SubscriptionChange setStatus(UUID id, SubscriptionChangeStatus status) {
        return setStatus(id, status, null);
    }

    private SubscriptionChange setStatus(UUID id, SubscriptionChangeStatus status, String error) {
        return transaction.execute(tx -> {
            var stored = changes.findById(id).orElseThrow();
            stored.setStatus(status);
            stored.setLastError(error);
            stored.setAttempts(0);
            stored.setNextAttemptAt(Instant.now());
            return stored;
        });
    }

    private SubscriptionChange recordFailure(UUID id, String error) {
        return transaction.execute(tx -> {
            var stored = changes.findById(id).orElseThrow();
            int attempts = stored.getAttempts() + 1;
            stored.setAttempts(attempts);
            stored.setLastError(error);
            stored.setNextAttemptAt(Instant.now().plusSeconds(Math.min(300, 5L << attempts)));
            if (attempts >= MAX_ATTEMPTS) {
                stored.setStatus(MANUAL_REVIEW);
                log.error("Subscription change {} needs manual review", id);
            }
            return stored;
        });
    }

    private ReentrantLock lock(UUID subscriptionId) {
        // The application runs as one instance. The database also enforces one active operation per subscription.
        return locks[Math.floorMod(subscriptionId.hashCode(), locks.length)];
    }

    private static SubscriptionChangeResult result(SubscriptionChange change) {
        return new SubscriptionChangeResult(change.getId(), change.getPrice(), change.getStatus());
    }
}

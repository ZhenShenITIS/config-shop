-- PostgreSQL 17. Проект DDL и маппингов; НЕ подключён к Liquibase.
-- Здесь нет переименования старых таблиц и INSERT переноса данных.
-- old_* ниже — старые бизнес-таблицы после переименования. Quartz и Liquibase не трогаем.
-- Имена новых ограничений и индексов имеют префикс v2_,
-- чтобы не конфликтовать с объектами переименованных таблиц.
--
-- Принятые решения для черновика:
-- 1. Все внутренние id и ссылки на них — UUID. DEFAULT gen_random_uuid() позволяет
--    не задавать id при обычной вставке. Telegram ID и числовой ID Remnawave остаются bigint.
--    При переносе каждой старой записи назначается новый UUID.
-- 2. Денежные значения bigint — рубли, как в текущем коде; без умножения на 100.
--    Сумма провайдера — numeric(20,2); дробные суммы не округлять при переносе автоматически.
-- 3. :migration_at — один зафиксированный момент переноса, не SQL-параметр этого DDL.
--    NULL старых дат заменять им только с фиксацией факта, что исходная дата неизвестна.
-- 4. Дополнения к Updated-db.md: причина ADMIN-корректировки; исходный платёж и уровень
--    реферального бонуса; currency и redirect платежа для сохранения текущего сценария.
--    web_users/email_confirmations — предложение, требующее согласования при разработке входа.
--
-- Подготовка маппинга: один раз назначить account_id каждому old_bot_users.id,
-- а также UUID его remnawave_subscription; сохранить соответствие для сверок и повторов.
-- Для каждой переносимой сущности сохранить пары «старый ID -> новый UUID».
-- Например: старый promo_codes.id = 42 -> новый promo_codes.id = назначенный UUID.
-- При переносе promo_code_uses ссылку promo_code_id = 42 заменить этим UUID.
-- При повторном переносе брать UUID из сохранённого соответствия.
-- Для старых кнопок вывода сохранить соответствие числового ID заявки новому UUID;
-- новые кнопки должны передавать UUID. Эти соответствия нужно реализовать в миграции.
-- A(telegram_id) ниже означает поиск bot_users.account_id по bot_users.telegram_id.
-- S(telegram_id) — подписка этого аккаунта; на момент переноса она одна.
-- JOIN должен возвращать ровно одну строку. Пропуски/дубли — ошибка переноса, не повод
-- использовать GROUP BY, LIMIT 1 или создавать фиктивную подписку.

-- 1. accounts <- old_bot_users (одна строка на пользователя)
-- id <- UUID из сохранённого соответствия old_bot_users.id -> account_id.
-- referral_percentage* <- одноимённые поля; NULL -> 35/20/10 как в текущем коде.
-- created_at, updated_at <- old_bot_users.created_at; коэффициенты <- 1.
-- balance НЕ переносится в accounts: используется для сверки balance_log.
CREATE TABLE public.accounts (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_accounts_pk PRIMARY KEY,
    referral_percentage1lvl smallint NOT NULL DEFAULT 35,
    referral_percentage2lvl smallint NOT NULL DEFAULT 20,
    referral_percentage3lvl smallint NOT NULL DEFAULT 10,
    subscription_price_rate numeric(12,6) NOT NULL DEFAULT 1,
    traffic_package_rate numeric(12,6) NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_accounts_referral_percentages_ck CHECK (
        referral_percentage1lvl BETWEEN 0 AND 100
        AND referral_percentage2lvl BETWEEN 0 AND 100
        AND referral_percentage3lvl BETWEEN 0 AND 100
    ),
    CONSTRAINT v2_accounts_rates_ck CHECK (subscription_price_rate > 0 AND traffic_package_rate > 0)
);

-- 2. bot_users <- old_bot_users
-- telegram_id <- id; name <- first_name; last_name, username <- одноимённые поля.
-- account_id <- сохранённое соответствие; created_at, updated_at <- created_at.
-- expire_at здесь больше нет: текущее состояние подписки запрашивается в Remnawave.
CREATE TABLE public.bot_users (
    telegram_id bigint CONSTRAINT v2_bot_users_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    name varchar(255),
    last_name varchar(255),
    username varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX v2_bot_users_account_idx ON public.bot_users (account_id);

-- 3. remnawave_subscriptions <- old_bot_users
-- id <- заранее назначенный UUID; account_id <- A(old_bot_users.id).
-- short_id, remnawave_uuid, remnawave_id, created_at <- одноимённые поля.
-- Должен существовать хотя бы один внешний идентификатор; UUID и числовой ID могут
-- быть NULL по отдельности (v2/v3). Отсутствие обоих разобрать до переноса.
-- Остаток времени/трафика и устройства в панели не изменяем и не создаём повторно.
CREATE TABLE public.remnawave_subscriptions (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_remnawave_subscriptions_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    short_id varchar(255),
    remnawave_uuid varchar(255),
    remnawave_id bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_remnawave_subscriptions_short_uq UNIQUE (short_id),
    CONSTRAINT v2_remnawave_subscriptions_uuid_uq UNIQUE (remnawave_uuid),
    CONSTRAINT v2_remnawave_subscriptions_remote_id_uq UNIQUE (remnawave_id),
    CONSTRAINT v2_remnawave_subscriptions_owner_uq UNIQUE (id, account_id),
    CONSTRAINT v2_remnawave_subscriptions_reference_ck CHECK (
        NULLIF(btrim(remnawave_uuid), '') IS NOT NULL OR remnawave_id IS NOT NULL
    ),
    CONSTRAINT v2_remnawave_subscriptions_remote_id_ck CHECK (remnawave_id > 0)
);
CREATE INDEX v2_remnawave_subscriptions_account_idx ON public.remnawave_subscriptions (account_id);

-- 4. promo_codes <- old_promo_codes
-- id <- новый UUID, сохранить пару old_promo_codes.id -> новый id.
-- amount, code, max_uses <- одноимённые поля; current_uses NULL -> 0;
-- is_referral NULL -> false; referrer_id <- A(old_promo_codes.referrer_id), если задан.
-- created_at отсутствует в старой таблице -> :migration_at (исходная дата неизвестна).
-- Не менять регистр старых кодов без проверки коллизий и текущего поведения бота.
CREATE TABLE public.promo_codes (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_promo_codes_pk PRIMARY KEY,
    amount bigint NOT NULL,
    code varchar(255) NOT NULL,
    max_uses integer,
    current_uses integer NOT NULL DEFAULT 0,
    is_referral boolean NOT NULL DEFAULT false,
    referrer_id uuid REFERENCES public.accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_promo_codes_code_uq UNIQUE (code),
    CONSTRAINT v2_promo_codes_amount_ck CHECK (amount >= 0),
    CONSTRAINT v2_promo_codes_usage_ck CHECK (
        current_uses >= 0 AND (max_uses IS NULL OR (max_uses >= 0 AND current_uses <= max_uses))
    ),
    CONSTRAINT v2_promo_codes_referrer_ck CHECK (NOT is_referral OR referrer_id IS NOT NULL)
);
CREATE INDEX v2_promo_codes_referrer_idx ON public.promo_codes (referrer_id);

-- 5. promo_code_uses <- old_promo_code_uses
-- id <- новый UUID, сохранить пару old_promo_code_uses.id -> новый id.
-- promo_code_id <- UUID нового промокода по сохранённому соответствию старому promo_code_id.
-- created_at <- created_at; account_id <- A(bot_user_id).
CREATE TABLE public.promo_code_uses (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_promo_code_uses_pk PRIMARY KEY,
    promo_code_id uuid NOT NULL REFERENCES public.promo_codes(id),
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_promo_code_uses_once_uq UNIQUE (promo_code_id, account_id)
);
CREATE INDEX v2_promo_code_uses_account_idx ON public.promo_code_uses (account_id);

-- 6. referrals <- old_referrals
-- id <- новый UUID (сохранить соответствие старому ID для сверки).
-- referrer_id <- A(old_referrals.referrer_id); referral_id <- A(old_referrals.referral_id).
-- created_at <- created_at. До переноса проверить циклы: CHECK ниже запрещает только самоссылку.
CREATE TABLE public.referrals (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_referrals_pk PRIMARY KEY,
    referrer_id uuid NOT NULL REFERENCES public.accounts(id),
    referral_id uuid NOT NULL REFERENCES public.accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_referrals_referral_uq UNIQUE (referral_id),
    CONSTRAINT v2_referrals_self_ck CHECK (referrer_id <> referral_id)
);
CREATE INDEX v2_referrals_referrer_idx ON public.referrals (referrer_id);

-- 7. platega_payments <- ВСЕ old_platega_payments, включая PENDING/CANCELED
-- id <- новый UUID; provider_payment_id <- transaction_id (строка, не принудительный UUID).
-- amount <- amount::numeric(20,2); currency, status, payment_method, redirect <- исходные поля.
-- account_id <- A(t.bot_user_id), где t из old_top_ups:
--   t.top_up_source = 'EXTERNAL' AND t.external_id = old_platega_payments.transaction_id.
-- Если связи нет (например, PENDING), оставляем платёж в old_platega_payments
-- и отчёте для ручной проверки; принятый риск не блокирует переключение.
-- created_at, updated_at -> :migration_at: в старой таблице этих дат нет.
-- Несовпадения валют/сумм и отсутствующие платежи в EXTERNAL top_up блокируют перенос.
-- Если старое пополнение уже существует при PENDING-статусе платежа, сохраняем оба факта
-- и отмечаем расхождение в отчёте. Повторное зачисление запрещено по существующему top_up.
-- Старые PENDING без владельца — принятое исключение; не создавать вымышленные платежи.
-- payment_details, return_url, crypto_amount, usdt_rate остаются в old_* как архив;
-- перед удалением архива проверить, что приложению они не нужны либо доступны через API.
CREATE TABLE public.platega_payments (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_platega_payments_pk PRIMARY KEY,
    provider_payment_id varchar(255) NOT NULL,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    amount numeric(20,2) NOT NULL,
    currency varchar(3) NOT NULL,
    status varchar(32) NOT NULL,
    payment_method varchar(32) NOT NULL,
    redirect text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_platega_payments_provider_uq UNIQUE (provider_payment_id),
    CONSTRAINT v2_platega_payments_owner_uq UNIQUE (id, account_id),
    CONSTRAINT v2_platega_payments_amount_ck CHECK (amount > 0),
    CONSTRAINT v2_platega_payments_status_ck CHECK (
        status IN ('PENDING', 'CONFIRMED', 'CANCELED', 'CHARGEBACKED')
    ),
    CONSTRAINT v2_platega_payments_method_ck CHECK (
        payment_method IN ('SBPQR', 'Crypto', 'CardRu', 'Card', 'International')
    )
);
CREATE INDEX v2_platega_payments_account_idx ON public.platega_payments (account_id, created_at DESC);

-- 8. purchases <- old_purchases
-- id <- новый UUID, сохранить пару old_purchases.id -> новый id.
-- paid_amount, created_at <- исходные поля; type <- purchase_type.
-- account_id <- A(bot_user_id); remnawave_subscription_id <- S(bot_user_id).
-- SUBSCRIPTION: sub_days/sub_devices <- old_subscriptions.duration_days/device_count
--   по old_purchases.subscription_id; traffic_gb/device_count -> NULL.
-- TRAFFIC: traffic_gb <- traffic_gb; прочие параметры -> NULL.
-- DEVICE: device_count <- device_count; прочие параметры -> NULL (старые и новые покупки устройств).
-- account_price_rate <- 1: исторический коэффициент неизвестен, paid_amount сохраняем точно.
-- Нет тарифа/подписки/обязательного параметра — разбор, а не текущий тариф вместо старого.
CREATE TABLE public.purchases (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_purchases_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    remnawave_subscription_id uuid NOT NULL,
    paid_amount bigint NOT NULL,
    account_price_rate numeric(12,6) NOT NULL DEFAULT 1,
    type varchar(32) NOT NULL,
    traffic_gb integer,
    sub_days integer,
    sub_devices integer,
    device_count integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_purchases_owner_uq UNIQUE (id, account_id),
    CONSTRAINT v2_purchases_subscription_fk FOREIGN KEY (remnawave_subscription_id, account_id)
        REFERENCES public.remnawave_subscriptions (id, account_id),
    CONSTRAINT v2_purchases_amount_ck CHECK (paid_amount >= 0 AND account_price_rate > 0),
    CONSTRAINT v2_purchases_type_ck CHECK (type IN ('SUBSCRIPTION', 'TRAFFIC', 'DEVICE')),
    CONSTRAINT v2_purchases_parameters_ck CHECK (
        (type = 'SUBSCRIPTION' AND sub_days IS NOT NULL AND sub_days > 0
            AND sub_devices IS NOT NULL AND sub_devices > 0 AND traffic_gb IS NULL AND device_count IS NULL)
        OR (type = 'TRAFFIC' AND traffic_gb IS NOT NULL AND traffic_gb > 0
            AND sub_days IS NULL AND sub_devices IS NULL AND device_count IS NULL)
        OR (type = 'DEVICE' AND device_count IS NOT NULL AND device_count > 0
            AND sub_days IS NULL AND sub_devices IS NULL AND traffic_gb IS NULL)
    )
);
CREATE INDEX v2_purchases_account_idx ON public.purchases (account_id, created_at DESC);
CREATE INDEX v2_purchases_subscription_idx ON public.purchases (remnawave_subscription_id, account_id);

-- 9. top_ups <- old_top_ups
-- id <- новый UUID, сохранить пару old_top_ups.id -> новый id.
-- value (включая отрицательные ADMIN), top_up_source, created_at <- исходные поля.
-- account_id <- A(bot_user_id); referral_id <- A(referral_id), если задан.
-- EXTERNAL: platega_id <- platega_payments.id по provider_payment_id = external_id.
-- PROMO_CODE: promo_code_id <- promo_codes.id по code = external_id.
-- Остальные source-ссылки -> NULL; неоднозначные/отсутствующие ссылки разобрать до переноса.
-- reason <- NULL для старых строк; для новой корректировки баланса — 'MIGRATION_BALANCE_ALIGNMENT'.
-- source_payment_id/referral_level — предложенная связь бонуса с исходным платежом и уровнем.
-- В старой схеме её нет: переносить NULL, не восстанавливать по совпадению сумм или дат.
-- Для новых реферальных начислений приложение обязано заполнять оба поля.
-- UNIQUE(platega_id) допускает одно внешнее зачисление: возврат/chargeback потребует
-- отдельного решения по корректирующей операции; второй EXTERNAL top_up с тем же ID не вставлять.
CREATE TABLE public.top_ups (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_top_ups_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    value bigint NOT NULL,
    top_up_source varchar(32) NOT NULL,
    platega_id uuid,
    promo_code_id uuid REFERENCES public.promo_codes(id),
    referral_id uuid REFERENCES public.accounts(id),
    source_payment_id uuid,
    referral_level smallint,
    reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_top_ups_owner_uq UNIQUE (id, account_id),
    CONSTRAINT v2_top_ups_platega_uq UNIQUE (platega_id),
    CONSTRAINT v2_top_ups_payment_fk FOREIGN KEY (platega_id, account_id)
        REFERENCES public.platega_payments (id, account_id),
    CONSTRAINT v2_top_ups_referral_payment_fk FOREIGN KEY (source_payment_id, referral_id)
        REFERENCES public.platega_payments (id, account_id),
    CONSTRAINT v2_top_ups_source_ck CHECK (top_up_source IN ('REFERRAL', 'EXTERNAL', 'PROMO_CODE', 'ADMIN')),
    CONSTRAINT v2_top_ups_source_links_ck CHECK (
        (top_up_source = 'EXTERNAL' AND platega_id IS NOT NULL AND promo_code_id IS NULL AND referral_id IS NULL)
        OR (top_up_source = 'PROMO_CODE' AND promo_code_id IS NOT NULL AND platega_id IS NULL AND referral_id IS NULL)
        OR (top_up_source = 'REFERRAL' AND referral_id IS NOT NULL AND platega_id IS NULL AND promo_code_id IS NULL)
        OR (top_up_source = 'ADMIN' AND platega_id IS NULL AND promo_code_id IS NULL AND referral_id IS NULL)
    ),
    CONSTRAINT v2_top_ups_referral_payment_ck CHECK (
        (source_payment_id IS NULL AND referral_level IS NULL)
        OR (top_up_source = 'REFERRAL' AND source_payment_id IS NOT NULL
            AND referral_level IS NOT NULL AND referral_level BETWEEN 1 AND 3)
    ),
    CONSTRAINT v2_top_ups_value_ck CHECK (top_up_source = 'ADMIN' OR value > 0)
);
CREATE INDEX v2_top_ups_account_idx ON public.top_ups (account_id, created_at DESC);
CREATE UNIQUE INDEX v2_top_ups_referral_reward_uq
    ON public.top_ups (source_payment_id, account_id, referral_level) WHERE source_payment_id IS NOT NULL;
CREATE INDEX v2_top_ups_promo_code_idx ON public.top_ups (promo_code_id);
CREATE INDEX v2_top_ups_referral_idx ON public.top_ups (referral_id);

-- 10. withdrawals <- old_withdrawals
-- id <- новый UUID, сохранить пару old_withdrawals.id (включая TSID) -> новый id.
-- amount, status, type, requisites, created_at <- исходные поля.
-- account_id <- A(bot_user_id); updated_at <- created_at (дата изменения статуса неизвестна).
-- Старые кнопки разрешают числовой ID через сохранённое соответствие новому UUID.
CREATE TABLE public.withdrawals (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_withdrawals_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    amount bigint NOT NULL,
    status varchar(32) NOT NULL,
    type varchar(16) NOT NULL,
    requisites text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_withdrawals_owner_uq UNIQUE (id, account_id),
    CONSTRAINT v2_withdrawals_amount_ck CHECK (amount > 0),
    CONSTRAINT v2_withdrawals_status_ck CHECK (status IN ('REJECTED', 'DONE', 'IN_PROGRESS')),
    CONSTRAINT v2_withdrawals_type_ck CHECK (type IN ('CARD', 'CRYPTO'))
);
CREATE INDEX v2_withdrawals_account_idx ON public.withdrawals (account_id, created_at DESC);

-- 11. balance_log <- новые purchases/top_ups/withdrawals после переноса
-- id <- новый UUID; account_id и ссылка на источник <- соответствующая новая строка.
-- Покупка: amount = -paid_amount; тип SUBSCRIPTION_PURCHASE/TRAFFIC_PURCHASE/DEVICE_PURCHASE.
-- Пополнение: amount = value; тип TOP_UP_EXTERNAL/TOP_UP_REFERRAL/TOP_UP_PROMO_CODE/ADMIN.
-- Вывод любой итоговой стадии: amount = -amount, тип WITHDRAWAL, дата <- created_at.
-- REJECTED: дополнительно +amount, тип WITHDRAWAL_REJECT; дата возврата неизвестна,
-- использовать :migration_at с отметкой в отчёте, а не выдумывать исторический момент.
-- Остальные даты <- created_at источника. balance_log для ADMIN-корректировки также
-- ссылается на top_up; разница = old_bot_users.balance - SUM(balance_log.amount).
-- Корректировку добавлять только при ненулевой разнице и с сохранением отчёта сверки.
-- Сумму и тип записи сверяет с источником сервис; при списании он блокирует строку accounts.
CREATE TABLE public.balance_log (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_balance_log_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    amount bigint NOT NULL,
    type varchar(32) NOT NULL,
    purchase_id uuid,
    top_up_id uuid,
    withdrawal_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_balance_log_purchase_fk FOREIGN KEY (purchase_id, account_id)
        REFERENCES public.purchases (id, account_id),
    CONSTRAINT v2_balance_log_top_up_fk FOREIGN KEY (top_up_id, account_id)
        REFERENCES public.top_ups (id, account_id),
    CONSTRAINT v2_balance_log_withdrawal_fk FOREIGN KEY (withdrawal_id, account_id)
        REFERENCES public.withdrawals (id, account_id),
    CONSTRAINT v2_balance_log_one_source_ck CHECK (num_nonnulls(purchase_id, top_up_id, withdrawal_id) = 1),
    CONSTRAINT v2_balance_log_type_ck CHECK (type IN (
        'SUBSCRIPTION_PURCHASE', 'TRAFFIC_PURCHASE', 'DEVICE_PURCHASE',
        'TOP_UP_EXTERNAL', 'TOP_UP_REFERRAL', 'TOP_UP_PROMO_CODE', 'ADMIN',
        'WITHDRAWAL', 'WITHDRAWAL_REJECT'
    )),
    CONSTRAINT v2_balance_log_type_source_ck CHECK (
        (purchase_id IS NOT NULL AND type IN ('SUBSCRIPTION_PURCHASE', 'TRAFFIC_PURCHASE', 'DEVICE_PURCHASE'))
        OR (top_up_id IS NOT NULL AND type IN ('TOP_UP_EXTERNAL', 'TOP_UP_REFERRAL', 'TOP_UP_PROMO_CODE', 'ADMIN'))
        OR (withdrawal_id IS NOT NULL AND type IN ('WITHDRAWAL', 'WITHDRAWAL_REJECT'))
    ),
    CONSTRAINT v2_balance_log_amount_sign_ck CHECK (
        (type IN ('SUBSCRIPTION_PURCHASE', 'TRAFFIC_PURCHASE', 'DEVICE_PURCHASE') AND amount <= 0)
        OR (type IN ('TOP_UP_EXTERNAL', 'TOP_UP_REFERRAL', 'TOP_UP_PROMO_CODE', 'WITHDRAWAL_REJECT') AND amount > 0)
        OR (type = 'WITHDRAWAL' AND amount < 0)
        OR type = 'ADMIN'
    )
);
CREATE INDEX v2_balance_log_history_idx ON public.balance_log (account_id, created_at DESC) INCLUDE (amount, type);
CREATE UNIQUE INDEX v2_balance_log_purchase_uq ON public.balance_log (purchase_id) WHERE purchase_id IS NOT NULL;
CREATE UNIQUE INDEX v2_balance_log_top_up_uq ON public.balance_log (top_up_id) WHERE top_up_id IS NOT NULL;
CREATE UNIQUE INDEX v2_balance_log_withdrawal_uq ON public.balance_log (withdrawal_id, type) WHERE withdrawal_id IS NOT NULL;

-- 12. subscription_adjustments: старого источника нет, при переносе пустая.
-- Новые строки создаются при пересчёте устройств; все old/new значения — снимок
-- состояния до/после операции, conversion_factor и pricing_policy_version — применённые правила.
CREATE TABLE public.subscription_adjustments (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_subscription_adjustments_pk PRIMARY KEY,
    subscription_id uuid NOT NULL REFERENCES public.remnawave_subscriptions(id),
    old_devices integer NOT NULL,
    new_devices integer NOT NULL,
    old_expires_at timestamptz NOT NULL,
    new_expires_at timestamptz NOT NULL,
    conversion_factor numeric(12,6) NOT NULL,
    pricing_policy_version varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_subscription_adjustments_devices_ck CHECK (old_devices > 0 AND new_devices > 0),
    CONSTRAINT v2_subscription_adjustments_factor_ck CHECK (conversion_factor > 0 AND conversion_factor <= 1)
);
CREATE INDEX v2_subscription_adjustments_subscription_idx
    ON public.subscription_adjustments (subscription_id, created_at DESC);

-- 13. web_users: старого источника нет, при переносе пустая; это предложение для web-этапа.
-- Новая регистрация задаёт account_id, email, хеш пароля и имя; пароль в открытом виде не хранится.
-- Статусы предложены: PENDING_EMAIL -> ACTIVE после подтверждения, BLOCKED — блокировка.
CREATE TABLE public.web_users (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_web_users_pk PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    email varchar(320) NOT NULL,
    password_hash text NOT NULL,
    name varchar(255),
    status varchar(32) NOT NULL DEFAULT 'PENDING_EMAIL',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_web_users_status_ck CHECK (status IN ('PENDING_EMAIL', 'ACTIVE', 'BLOCKED'))
);
CREATE UNIQUE INDEX v2_web_users_email_uq ON public.web_users (lower(email));
CREATE INDEX v2_web_users_account_idx ON public.web_users (account_id);

-- 14. email_confirmations: старого источника нет, при переносе пустая; предложение для web-этапа.
-- web_user_id <- web_users.id; token_hash <- SHA-256 случайного токена (hex),
-- created_at/expires_at <- выпуск и срок действия, used_at <- успешное подтверждение.
CREATE TABLE public.email_confirmations (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_email_confirmations_pk PRIMARY KEY,
    web_user_id uuid NOT NULL REFERENCES public.web_users(id),
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    CONSTRAINT v2_email_confirmations_token_uq UNIQUE (token_hash),
    CONSTRAINT v2_email_confirmations_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT v2_email_confirmations_used_ck CHECK (used_at IS NULL OR used_at >= created_at)
);
CREATE INDEX v2_email_confirmations_user_idx ON public.email_confirmations (web_user_id);

-- Последовательностей для внутренних ID нет; setval после переноса не нужен.
-- old_subscriptions и old_traffic_packages не имеют новых таблиц-аналогов:
-- первые нужны для снимков старых покупок, новые тарифы рассчитываются из конфигурации.
-- Обязательные сверки: 1 old_bot_user = 1 account = 1 bot_user = 1 remnawave_subscription;
-- количество перенесённых бизнес-строк, все ссылки, PENDING-платежи, баланс каждого аккаунта.
-- Строгие NOT NULL/CHECK могут выявить старые NULL, дубли и некорректные значения:
-- сначала отчёт и разбор, затем перенос; не пропускать строки ради успешного запуска.

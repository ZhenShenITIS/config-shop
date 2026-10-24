--liquibase formatted sql
--changeset config-shop:002-account-schema dbms:postgresql splitStatements:false
--comment: Account model and audited data transfer; all statements execute in one transaction.
ALTER TABLE public.bot_users RENAME TO old_bot_users;
ALTER TABLE public.referrals RENAME TO old_referrals;
ALTER TABLE public.subscriptions RENAME TO old_subscriptions;
ALTER TABLE public.traffic_packages RENAME TO old_traffic_packages;
ALTER TABLE public.promo_codes RENAME TO old_promo_codes;
ALTER TABLE public.promo_code_uses RENAME TO old_promo_code_uses;
ALTER TABLE public.purchases RENAME TO old_purchases;
ALTER TABLE public.top_ups RENAME TO old_top_ups;
ALTER TABLE public.withdrawals RENAME TO old_withdrawals;
ALTER TABLE public.platega_payments RENAME TO old_platega_payments;
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

CREATE TABLE public.promo_code_uses (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_promo_code_uses_pk PRIMARY KEY,
    promo_code_id uuid NOT NULL REFERENCES public.promo_codes(id),
    account_id uuid NOT NULL REFERENCES public.accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_promo_code_uses_once_uq UNIQUE (promo_code_id, account_id)
);
CREATE INDEX v2_promo_code_uses_account_idx ON public.promo_code_uses (account_id);

CREATE TABLE public.referrals (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT v2_referrals_pk PRIMARY KEY,
    referrer_id uuid NOT NULL REFERENCES public.accounts(id),
    referral_id uuid NOT NULL REFERENCES public.accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT v2_referrals_referral_uq UNIQUE (referral_id),
    CONSTRAINT v2_referrals_self_ck CHECK (referrer_id <> referral_id)
);
CREATE INDEX v2_referrals_referrer_idx ON public.referrals (referrer_id);

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


CREATE TABLE public.migration_id_map (
    entity text NOT NULL, old_id text NOT NULL, new_id uuid NOT NULL DEFAULT gen_random_uuid(),
    PRIMARY KEY(entity, old_id), UNIQUE(entity, new_id)
);
CREATE TABLE public.migration_report (
    entity text NOT NULL, old_id text NOT NULL, reason text NOT NULL,
    old_balance bigint, reconstructed_balance numeric, correction numeric,
    created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO migration_id_map(entity, old_id) SELECT 'bot_users', id::text FROM old_bot_users;
INSERT INTO migration_id_map(entity, old_id) SELECT 'referrals', id::text FROM old_referrals;
INSERT INTO migration_id_map(entity, old_id) SELECT 'promo_codes', id::text FROM old_promo_codes;
INSERT INTO migration_id_map(entity, old_id) SELECT 'promo_code_uses', id::text FROM old_promo_code_uses;
INSERT INTO migration_id_map(entity, old_id) SELECT 'purchases', id::text FROM old_purchases;
INSERT INTO migration_id_map(entity, old_id) SELECT 'top_ups', id::text FROM old_top_ups;
INSERT INTO migration_id_map(entity, old_id) SELECT 'withdrawals', id::text FROM old_withdrawals;
INSERT INTO migration_id_map(entity, old_id) SELECT 'platega_payments', transaction_id::text FROM old_platega_payments;
INSERT INTO migration_id_map(entity, old_id) SELECT 'remnawave_subscriptions', id::text FROM old_bot_users;

INSERT INTO accounts(id, referral_percentage1lvl, referral_percentage2lvl, referral_percentage3lvl, created_at, updated_at)
SELECT m.new_id, coalesce(u.referral_percentage1lvl,35), coalesce(u.referral_percentage2lvl,20),
       coalesce(u.referral_percentage3lvl,10), coalesce(u.created_at,now()), coalesce(u.created_at,now())
FROM old_bot_users u JOIN migration_id_map m ON m.entity='bot_users' AND m.old_id=u.id::text;
INSERT INTO bot_users(telegram_id, account_id, name, last_name, username, created_at, updated_at)
SELECT u.id, m.new_id, u.first_name, u.last_name, u.username, coalesce(u.created_at,now()), coalesce(u.created_at,now())
FROM old_bot_users u JOIN migration_id_map m ON m.entity='bot_users' AND m.old_id=u.id::text;
INSERT INTO remnawave_subscriptions(id,account_id,short_id,remnawave_uuid,remnawave_id,created_at)
SELECT m.new_id,b.account_id,u.short_id,u.remnawave_uuid,u.remnawave_id,coalesce(u.created_at,now())
FROM old_bot_users u JOIN bot_users b ON b.telegram_id=u.id
JOIN migration_id_map m ON m.entity='remnawave_subscriptions' AND m.old_id=u.id::text;
INSERT INTO promo_codes(id,code,amount,max_uses,current_uses,is_referral,referrer_id,created_at)
SELECT m.new_id,p.code,p.amount,p.max_uses,coalesce(p.current_uses,0),coalesce(p.is_referral,false),b.account_id,now()
FROM old_promo_codes p JOIN migration_id_map m ON m.entity='promo_codes' AND m.old_id=p.id::text
LEFT JOIN bot_users b ON b.telegram_id=p.referrer_id;
INSERT INTO promo_code_uses(id,promo_code_id,account_id,created_at)
SELECT m.new_id,pm.new_id,b.account_id,coalesce(p.created_at,now()) FROM old_promo_code_uses p
JOIN migration_id_map m ON m.entity='promo_code_uses' AND m.old_id=p.id::text
LEFT JOIN migration_id_map pm ON pm.entity='promo_codes' AND pm.old_id=p.promo_code_id::text
LEFT JOIN bot_users b ON b.telegram_id=p.bot_user_id;
INSERT INTO referrals(id,referrer_id,referral_id,created_at)
SELECT m.new_id,b.account_id,c.account_id,coalesce(r.created_at,now()) FROM old_referrals r
JOIN migration_id_map m ON m.entity='referrals' AND m.old_id=r.id::text
LEFT JOIN bot_users b ON b.telegram_id=r.referrer_id
LEFT JOIN bot_users c ON c.telegram_id=r.referral_id;
DO $$ BEGIN
 IF EXISTS (
   WITH RECURSIVE paths AS (
     SELECT referrer_id AS origin, referral_id AS node, ARRAY[referrer_id,referral_id] AS visited, false AS cycle FROM referrals
     UNION ALL
     SELECT p.origin,r.referral_id,p.visited||r.referral_id,r.referral_id=ANY(p.visited)
     FROM paths p JOIN referrals r ON r.referrer_id=p.node WHERE NOT p.cycle
   ) SELECT 1 FROM paths WHERE cycle
 ) THEN RAISE EXCEPTION 'Referral cycle detected'; END IF;
END $$;
-- A payment owner is inferred only from an EXTERNAL top-up with the same provider ID.
DO $$ BEGIN
 IF EXISTS (SELECT external_id FROM old_top_ups WHERE top_up_source='EXTERNAL'
            GROUP BY external_id HAVING count(DISTINCT bot_user_id)>1)
 THEN RAISE EXCEPTION 'Ambiguous payment owner'; END IF;
END $$;
-- Validate original precision before casting provider amounts to numeric(20,2).
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM old_top_ups t JOIN old_platega_payments p ON p.transaction_id=t.external_id
            WHERE t.top_up_source='EXTERNAL' AND (p.currency <> 'RUB' OR p.amount::numeric <> t.value))
 THEN RAISE EXCEPTION 'External top-up differs from original provider amount/currency'; END IF;
END $$;
INSERT INTO platega_payments(id,provider_payment_id,account_id,amount,currency,status,payment_method,redirect)
SELECT m.new_id,p.transaction_id,b.account_id,p.amount::numeric(20,2),p.currency,p.status,p.payment_method,p.redirect
FROM old_platega_payments p JOIN migration_id_map m ON m.entity='platega_payments' AND m.old_id=p.transaction_id
JOIN (SELECT external_id,min(bot_user_id) AS bot_user_id FROM old_top_ups WHERE top_up_source='EXTERNAL' GROUP BY external_id) t
 ON t.external_id=p.transaction_id
JOIN bot_users b ON b.telegram_id=t.bot_user_id;
INSERT INTO migration_report(entity,old_id,reason)
SELECT 'platega_payments',p.transaction_id,'UNRESOLVED_OWNER_MANUAL_REVIEW' FROM old_platega_payments p
WHERE NOT EXISTS (SELECT 1 FROM platega_payments n WHERE n.provider_payment_id=p.transaction_id);
INSERT INTO purchases(id,account_id,remnawave_subscription_id,paid_amount,type,sub_days,sub_devices,traffic_gb,device_count,created_at)
SELECT m.new_id,b.account_id,sm.new_id,p.paid_amount,p.purchase_type,
 CASE WHEN p.purchase_type='SUBSCRIPTION' THEN t.duration_days END,
 CASE WHEN p.purchase_type='SUBSCRIPTION' THEN t.device_count END,
 CASE WHEN p.purchase_type='TRAFFIC' THEN p.traffic_gb END,
 CASE WHEN p.purchase_type='DEVICE' THEN p.device_count END,coalesce(p.created_at,now())
FROM old_purchases p JOIN migration_id_map m ON m.entity='purchases' AND m.old_id=p.id::text
LEFT JOIN bot_users b ON b.telegram_id=p.bot_user_id
LEFT JOIN migration_id_map sm ON sm.entity='remnawave_subscriptions' AND sm.old_id=p.bot_user_id::text
LEFT JOIN old_subscriptions t ON t.id=p.subscription_id;
INSERT INTO top_ups(id,account_id,value,top_up_source,platega_id,promo_code_id,referral_id,created_at)
SELECT m.new_id,b.account_id,t.value,t.top_up_source,
 CASE WHEN t.top_up_source='EXTERNAL' THEN p.id END,
 CASE WHEN t.top_up_source='PROMO_CODE' THEN c.id END,
 CASE WHEN t.top_up_source='REFERRAL' THEN r.account_id END,coalesce(t.created_at,now())
FROM old_top_ups t JOIN migration_id_map m ON m.entity='top_ups' AND m.old_id=t.id::text
LEFT JOIN bot_users b ON b.telegram_id=t.bot_user_id
LEFT JOIN bot_users r ON r.telegram_id=t.referral_id
LEFT JOIN platega_payments p ON p.provider_payment_id=t.external_id
LEFT JOIN promo_codes c ON c.code=t.external_id;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM top_ups t JOIN platega_payments p ON p.id=t.platega_id
            WHERE p.currency <> 'RUB' OR p.amount <> t.value )
 THEN RAISE EXCEPTION 'External top-up differs from RUB payment amount'; END IF;
END $$;
-- Preserve provider status even when old code already credited a PENDING payment.
INSERT INTO migration_report(entity,old_id,reason)
SELECT 'platega_payments',p.provider_payment_id,'CREDITED_PAYMENT_STATUS_'||p.status
FROM platega_payments p JOIN top_ups t ON t.platega_id=p.id WHERE p.status<>'CONFIRMED';
INSERT INTO withdrawals(id,account_id,amount,status,type,requisites,created_at,updated_at)
SELECT m.new_id,b.account_id,w.amount,w.status,w.type,w.requisites,coalesce(w.created_at,now()),coalesce(w.created_at,now())
FROM old_withdrawals w JOIN migration_id_map m ON m.entity='withdrawals' AND m.old_id=w.id::text
LEFT JOIN bot_users b ON b.telegram_id=w.bot_user_id;
INSERT INTO balance_log(account_id,amount,type,purchase_id,created_at)
SELECT account_id,-paid_amount,type||'_PURCHASE',id,created_at FROM purchases;
INSERT INTO balance_log(account_id,amount,type,top_up_id,created_at)
SELECT account_id,value,CASE top_up_source WHEN 'ADMIN' THEN 'ADMIN' ELSE 'TOP_UP_'||top_up_source END,id,created_at FROM top_ups;
INSERT INTO balance_log(account_id,amount,type,withdrawal_id,created_at)
SELECT account_id,-amount,'WITHDRAWAL',id,created_at FROM withdrawals;
INSERT INTO balance_log(account_id,amount,type,withdrawal_id,created_at)
SELECT account_id,amount,'WITHDRAWAL_REJECT',id,now() FROM withdrawals WHERE status='REJECTED';
INSERT INTO migration_report(entity,old_id,reason)
SELECT 'withdrawals',m.old_id,'REJECT_DATE_UNKNOWN_USING_MIGRATION_TIME' FROM withdrawals w
JOIN migration_id_map m ON m.entity='withdrawals' AND m.new_id=w.id WHERE w.status='REJECTED';
INSERT INTO migration_report(entity,old_id,reason,old_balance,reconstructed_balance,correction)
SELECT 'bot_users',u.id::text,'BALANCE_RECONCILIATION',u.balance,coalesce(sum(l.amount),0),u.balance-coalesce(sum(l.amount),0)
FROM old_bot_users u JOIN bot_users b ON b.telegram_id=u.id LEFT JOIN balance_log l ON l.account_id=b.account_id
GROUP BY u.id,u.balance;
WITH adjustments AS (
 INSERT INTO top_ups(account_id,value,top_up_source,reason)
 SELECT b.account_id,r.correction::bigint,'ADMIN','MIGRATION_BALANCE_ALIGNMENT'
 FROM migration_report r JOIN bot_users b ON b.telegram_id::text=r.old_id
 WHERE r.reason='BALANCE_RECONCILIATION' AND r.correction<>0 RETURNING *
)
INSERT INTO balance_log(account_id,amount,type,top_up_id,created_at)
SELECT account_id,value,'ADMIN',id,created_at FROM adjustments;
DO $$ DECLARE entity_name text; old_count bigint; new_count bigint; BEGIN
 FOREACH entity_name IN ARRAY ARRAY['bot_users','referrals','promo_codes','promo_code_uses','purchases','withdrawals'] LOOP
  EXECUTE format('select count(*) from old_%I',entity_name) INTO old_count;
  EXECUTE format('select count(*) from %I',entity_name) INTO new_count;
  IF old_count<>new_count THEN RAISE EXCEPTION 'Row count mismatch: %',entity_name; END IF;
 END LOOP;
 IF (SELECT count(*) FROM accounts)<>(SELECT count(*) FROM old_bot_users)
 OR (SELECT count(*) FROM remnawave_subscriptions)<>(SELECT count(*) FROM old_bot_users)
 THEN RAISE EXCEPTION 'Account/subscription count mismatch'; END IF;
 IF (SELECT count(*) FROM top_ups WHERE reason IS DISTINCT FROM 'MIGRATION_BALANCE_ALIGNMENT')<>(SELECT count(*) FROM old_top_ups)
 THEN RAISE EXCEPTION 'Top-up count mismatch'; END IF;
 IF EXISTS (SELECT 1 FROM old_bot_users u JOIN bot_users b ON b.telegram_id=u.id
    LEFT JOIN balance_log l ON l.account_id=b.account_id GROUP BY u.id,u.balance HAVING coalesce(sum(l.amount),0)<>u.balance)
 THEN RAISE EXCEPTION 'Balance mismatch after migration'; END IF;
END $$;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'bot_users',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_bot_users WHERE created_at IS NULL;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'promo_code_uses',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_promo_code_uses WHERE created_at IS NULL;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'referrals',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_referrals WHERE created_at IS NULL;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'purchases',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_purchases WHERE created_at IS NULL;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'top_ups',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_top_ups WHERE created_at IS NULL;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'withdrawals',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_withdrawals WHERE created_at IS NULL;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'promo_codes',id::text,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM old_promo_codes;
INSERT INTO migration_report(entity,old_id,reason) SELECT 'platega_payments',provider_payment_id,'CREATED_AT_UNKNOWN_USING_MIGRATION_TIME' FROM platega_payments;

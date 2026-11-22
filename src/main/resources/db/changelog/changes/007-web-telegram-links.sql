--liquibase formatted sql
--changeset config-shop:007-web-telegram-links dbms:postgresql
CREATE TABLE public.web_telegram_links (
    web_user_id uuid PRIMARY KEY REFERENCES public.web_users(id),
    code_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);

-- Composite owner keys are checked at the end of the account merge transaction.
ALTER TABLE public.purchases ALTER CONSTRAINT v2_purchases_subscription_fk DEFERRABLE;
ALTER TABLE public.top_ups ALTER CONSTRAINT v2_top_ups_payment_fk DEFERRABLE;
ALTER TABLE public.top_ups ALTER CONSTRAINT v2_top_ups_referral_payment_fk DEFERRABLE;
ALTER TABLE public.balance_log ALTER CONSTRAINT v2_balance_log_purchase_fk DEFERRABLE;
ALTER TABLE public.balance_log ALTER CONSTRAINT v2_balance_log_top_up_fk DEFERRABLE;
ALTER TABLE public.balance_log ALTER CONSTRAINT v2_balance_log_withdrawal_fk DEFERRABLE;

ALTER TABLE public.bot_users ADD COLUMN preferred_subscription_id uuid;
ALTER TABLE public.bot_users ADD CONSTRAINT bot_users_preferred_subscription_fk
    FOREIGN KEY (preferred_subscription_id, account_id) REFERENCES public.remnawave_subscriptions(id, account_id)
    DEFERRABLE;

-- Two former accounts can each have a historical reward for the same payment and level.
ALTER TABLE public.top_ups ADD COLUMN original_reward_account_id uuid;
DROP INDEX public.v2_top_ups_referral_reward_uq;
CREATE UNIQUE INDEX v2_top_ups_referral_reward_uq
    ON public.top_ups (source_payment_id, COALESCE(original_reward_account_id, account_id), referral_level)
    WHERE source_payment_id IS NOT NULL;

--rollback DROP INDEX public.v2_top_ups_referral_reward_uq;
--rollback ALTER TABLE public.top_ups DROP COLUMN original_reward_account_id;
--rollback CREATE UNIQUE INDEX v2_top_ups_referral_reward_uq ON public.top_ups (source_payment_id, account_id, referral_level) WHERE source_payment_id IS NOT NULL;
--rollback ALTER TABLE public.bot_users DROP COLUMN preferred_subscription_id;
--rollback ALTER TABLE public.balance_log ALTER CONSTRAINT v2_balance_log_withdrawal_fk NOT DEFERRABLE;
--rollback ALTER TABLE public.balance_log ALTER CONSTRAINT v2_balance_log_top_up_fk NOT DEFERRABLE;
--rollback ALTER TABLE public.balance_log ALTER CONSTRAINT v2_balance_log_purchase_fk NOT DEFERRABLE;
--rollback ALTER TABLE public.top_ups ALTER CONSTRAINT v2_top_ups_referral_payment_fk NOT DEFERRABLE;
--rollback ALTER TABLE public.top_ups ALTER CONSTRAINT v2_top_ups_payment_fk NOT DEFERRABLE;
--rollback ALTER TABLE public.purchases ALTER CONSTRAINT v2_purchases_subscription_fk NOT DEFERRABLE;
--rollback DROP TABLE public.web_telegram_links;

--liquibase formatted sql
--changeset config-shop:005-email-confirmations dbms:postgresql
CREATE TABLE public.email_confirmations (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    web_user_id uuid NOT NULL REFERENCES public.web_users(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    referrer_account_id uuid REFERENCES public.accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    CONSTRAINT email_confirmations_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT email_confirmations_used_ck CHECK (used_at IS NULL OR used_at >= created_at)
);
CREATE INDEX email_confirmations_user_idx ON public.email_confirmations (web_user_id, created_at DESC);

--rollback DROP TABLE public.email_confirmations;

--liquibase formatted sql
--changeset config-shop:004-web-auth dbms:postgresql
CREATE TABLE public.web_users (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id uuid NOT NULL UNIQUE REFERENCES public.accounts(id),
    email varchar(320) NOT NULL,
    password_hash text NOT NULL,
    email_verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX web_users_email_uq ON public.web_users (lower(email));

CREATE TABLE public.web_refresh_tokens (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    web_user_id uuid NOT NULL REFERENCES public.web_users(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    family_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    replaced_by_id uuid REFERENCES public.web_refresh_tokens(id),
    CONSTRAINT web_refresh_tokens_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT web_refresh_tokens_used_ck CHECK (used_at IS NULL OR used_at >= created_at),
    CONSTRAINT web_refresh_tokens_revoked_ck CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT web_refresh_tokens_replacement_ck CHECK (replaced_by_id IS NULL OR replaced_by_id <> id)
);
CREATE INDEX web_refresh_tokens_user_idx ON public.web_refresh_tokens (web_user_id);
CREATE INDEX web_refresh_tokens_family_idx ON public.web_refresh_tokens (family_id);

--rollback DROP TABLE public.web_refresh_tokens;
--rollback DROP TABLE public.web_users;

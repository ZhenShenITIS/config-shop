--liquibase formatted sql
--changeset config-shop:006-web-password-resets dbms:postgresql
CREATE TABLE public.web_password_resets (
    web_user_id uuid PRIMARY KEY REFERENCES public.web_users(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT web_password_resets_expiry_ck CHECK (expires_at > created_at)
);

--rollback DROP TABLE public.web_password_resets;

--liquibase formatted sql

--changeset config-shop:008-web-payments
ALTER TABLE platega_payments
    ADD COLUMN request_id uuid UNIQUE,
    ALTER COLUMN provider_payment_id DROP NOT NULL,
    DROP CONSTRAINT v2_platega_payments_status_ck,
    ADD CONSTRAINT v2_platega_payments_status_ck CHECK (
        status IN ('CREATING', 'CREATION_UNCERTAIN', 'CREATION_FAILED', 'PENDING', 'CONFIRMED', 'CANCELED',
                   'DISPUTED', 'PARTIALLY_REFUNDED', 'CHARGEBACKED')
    ),
    ADD CONSTRAINT platega_payments_attempt_ck CHECK (
        (provider_payment_id IS NULL AND request_id IS NOT NULL
            AND status IN ('CREATING', 'CREATION_UNCERTAIN', 'CREATION_FAILED'))
        OR (provider_payment_id IS NOT NULL AND status NOT IN ('CREATING', 'CREATION_UNCERTAIN', 'CREATION_FAILED'))
    );

--changeset config-shop:008-subscription-changes
CREATE TABLE subscription_changes (
    id uuid PRIMARY KEY,
    subscription_id uuid NOT NULL REFERENCES remnawave_subscriptions(id),
    days integer NOT NULL CHECK (days >= 0),
    devices integer NOT NULL CHECK (devices > 0),
    price bigint NOT NULL CHECK (price >= 0),
    status varchar(32) NOT NULL CHECK (status IN ('PREPARING', 'APPLYING', 'APPLIED', 'COMPLETED',
                                                  'INSUFFICIENT_BALANCE', 'MANUAL_REVIEW')),
    purchase_id uuid UNIQUE REFERENCES purchases(id),
    previous_expiry timestamptz,
    previous_devices integer,
    target_expiry timestamptz,
    target_devices integer,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error text,
    CONSTRAINT subscription_changes_purchase_ck CHECK (
        (purchase_id IS NULL AND previous_expiry IS NULL AND previous_devices IS NULL AND target_expiry IS NULL
            AND target_devices IS NULL AND days > 0
            AND status IN ('PREPARING', 'INSUFFICIENT_BALANCE', 'MANUAL_REVIEW'))
        OR (purchase_id IS NOT NULL AND previous_expiry IS NOT NULL AND previous_devices IS NOT NULL
            AND target_expiry IS NOT NULL AND target_devices IS NOT NULL AND target_devices > 0 AND status IN ('APPLYING', 'APPLIED', 'COMPLETED', 'MANUAL_REVIEW'))
    )
);

CREATE UNIQUE INDEX subscription_changes_active_uq ON subscription_changes (subscription_id)
    WHERE status IN ('PREPARING', 'APPLYING', 'APPLIED', 'MANUAL_REVIEW');

CREATE INDEX subscription_changes_due_idx ON subscription_changes (next_attempt_at, id)
    WHERE status IN ('PREPARING', 'APPLYING', 'APPLIED');

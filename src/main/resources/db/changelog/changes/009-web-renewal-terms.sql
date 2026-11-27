--liquibase formatted sql

--changeset config-shop:009-web-renewal-terms
ALTER TABLE subscription_changes
    ADD COLUMN expected_expiry timestamptz,
    ADD COLUMN expiry_confirmed boolean NOT NULL DEFAULT false,
    DROP CONSTRAINT subscription_changes_status_check,
    DROP CONSTRAINT subscription_changes_purchase_ck,
    ADD CONSTRAINT subscription_changes_status_check CHECK (status IN
        ('PREPARING', 'APPLYING', 'APPLIED', 'COMPLETED', 'TERMS_CHANGED', 'INSUFFICIENT_BALANCE', 'MANUAL_REVIEW')),
    ADD CONSTRAINT subscription_changes_purchase_ck CHECK (
        (purchase_id IS NULL AND previous_expiry IS NULL AND previous_devices IS NULL AND target_expiry IS NULL
            AND target_devices IS NULL AND days > 0
            AND status IN ('PREPARING', 'TERMS_CHANGED', 'INSUFFICIENT_BALANCE', 'MANUAL_REVIEW'))
        OR (purchase_id IS NOT NULL AND previous_expiry IS NOT NULL AND previous_devices IS NOT NULL
            AND target_expiry IS NOT NULL AND target_devices > 0 AND target_devices IS NOT NULL
            AND status IN ('APPLYING', 'APPLIED', 'COMPLETED', 'MANUAL_REVIEW'))
    );

UPDATE subscription_changes SET expiry_confirmed = true
WHERE days > 0 AND status IN ('APPLIED', 'COMPLETED');

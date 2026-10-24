--liquibase formatted sql
--changeset config-shop:003-pending-payments-index dbms:postgresql
CREATE INDEX platega_payments_pending_idx ON public.platega_payments (created_at, id)
    WHERE status = 'PENDING';
--rollback DROP INDEX public.platega_payments_pending_idx;

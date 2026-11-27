import { Alert, Button, Group, Loader, Modal, Radio, Stack, Text } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  getGetDashboardQueryKey, getGetRenewalStatusQueryKey, getGetSubscriptionQueryKey,
  useConfirmRenewal, useGetRenewalOptions, useGetRenewalStatus,
  type RenewalOperation, type RenewalOptions, type RenewalRequest,
} from './api/generated/configShop';
import { ApiError } from './api/http';
import { ErrorAlert } from './auth';

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeStyle: 'short' });

export type RenewalDraft = {
  days?: RenewalRequest['days'];
  attempt?: RenewalRequest;
  topUpId?: string;
};

export type RenewalDrafts = Record<string, RenewalDraft>;

export function loadRenewalDrafts(accountId: string): RenewalDrafts {
  try {
    return JSON.parse(sessionStorage.getItem('config-shop:renewals:' + accountId) ?? '{}') ?? {};
  } catch {
    return {};
  }
}

export function storeRenewalDrafts(accountId: string, drafts: RenewalDrafts) {
  sessionStorage.setItem('config-shop:renewals:' + accountId, JSON.stringify(drafts));
}

type RenewalDialogProps = {
  id: string;
  title: string;
  balance: number;
  draft: RenewalDraft;
  opened: boolean;
  onChange: (draft: RenewalDraft) => void;
  onClose: () => void;
  onTopUp: (days: RenewalRequest['days'], amount: number) => void;
};

export function RenewalDialog({ id, title, balance, draft, opened, onChange, onClose, onTopUp }: RenewalDialogProps) {
  const client = useQueryClient();
  const [saveError, setSaveError] = useState<unknown>();
  const sending = useRef(false);
  const replayed = useRef<string | undefined>(undefined);
  const attempt = draft.attempt;
  const { mutateAsync, error: submitError, isPending: submitting, reset } = useConfirmRenewal();
  const options = useGetRenewalOptions(id, { query: { enabled: opened && !attempt, staleTime: 0 } });
  const status = useGetRenewalStatus(id, attempt?.id ?? '', { query: {
    enabled: Boolean(attempt) && !submitting,
    refetchInterval: query => {
      switch (query.state.data?.status) {
        case 'PREPARING':
        case 'APPLYING':
        case 'APPLIED': return 3000;
        default: return false;
      }
    },
  } });
  const operation = status.data;

  const submit = useCallback(async (request: RenewalRequest) => {
    if (sending.current) return;
    sending.current = true;
    try {
      const result = await mutateAsync({ id, data: request });
      client.setQueryData(getGetRenewalStatusQueryKey(id, request.id), result);
    } catch (error) {
      if (error instanceof ApiError && [400, 404, 409].includes(error.status)) {
        try {
          onChange({ days: request.days });
        } catch (storageError) {
          setSaveError(storageError);
        }
      }
    } finally {
      sending.current = false;
    }
  }, [client, id, mutateAsync, onChange]);

  useEffect(() => {
    if (!attempt || submitting || sending.current || replayed.current === attempt.id) return;
    if (status.error instanceof ApiError && status.error.status === 404 && !status.data) {
      replayed.current = attempt.id;
      void submit(attempt);
    }
  }, [attempt, status.data, status.error, submit, submitting]);

  const charged = operation?.charged;
  const expiresAt = operation?.expiresAt;
  useEffect(() => {
    if (charged || operation?.status === 'INSUFFICIENT_BALANCE') {
      void client.invalidateQueries({ queryKey: getGetDashboardQueryKey() });
    }
  }, [charged, client, operation?.id, operation?.status]);
  useEffect(() => {
    if (expiresAt) void client.invalidateQueries({ queryKey: getGetSubscriptionQueryKey(id) });
  }, [client, expiresAt, id]);

  function change(next: RenewalDraft, action?: () => void) {
    setSaveError(undefined);
    try {
      onChange(next);
      action?.();
    } catch (error) {
      setSaveError(error);
    }
  }

  function confirm(current: RenewalOptions, days: RenewalRequest['days'], price: number) {
    if (sending.current) return;
    const request: RenewalRequest = {
      id: crypto.randomUUID(), days, price, deviceLimit: current.deviceLimit, currentExpiresAt: current.currentExpiresAt,
    };
    change({ days, attempt: request }, () => void submit(request));
  }

  function refreshTerms() {
    reset();
    change({ days: draft.days }, () => void options.refetch());
  }

  function close() {
    if (operation?.status === 'COMPLETED') change({});
    onClose();
  }

  return <Modal opened={opened} onClose={close} title={title} centered>
    <Stack>
      <ErrorAlert error={saveError} />
      {attempt ? (
        operation
          ? <OperationStatus operation={operation} balance={balance} refreshTerms={refreshTerms}
              topUp={amount => onTopUp(attempt.days, amount)} />
          : submitting || status.isPending
            ? <Loader size="sm" aria-label="Проверяем продление" />
            : <Stack align="flex-start">
                <ErrorAlert error={submitError ?? status.error} />
                <Button variant="light" onClick={() => void submit(attempt)}>Повторить подтверждение</Button>
              </Stack>
      ) : (
        <>
          <ErrorAlert error={submitError} />
          {options.isFetching
            ? <Loader size="sm" aria-label="Загружаем условия продления" />
            : options.data && !options.isError
              ? <RenewalForm options={options.data} days={draft.days} balance={balance}
                  onDays={days => change({ ...draft, days })} confirm={confirm} topUp={onTopUp} />
              : <Stack align="flex-start">
                  <ErrorAlert error={options.error} />
                  <Button variant="light" onClick={() => void options.refetch()}>Повторить</Button>
                </Stack>}
        </>
      )}
    </Stack>
  </Modal>;
}

function RenewalForm({ options, days, balance, onDays, confirm, topUp }: {
  options: RenewalOptions;
  days?: RenewalRequest['days'];
  balance: number;
  onDays: (days: RenewalRequest['days']) => void;
  confirm: (options: RenewalOptions, days: RenewalRequest['days'], price: number) => void;
  topUp: (days: RenewalRequest['days'], amount: number) => void;
}) {
  const selected = options.options.find(option => option.days === days) ?? options.options[0];
  if (!selected) return <Stack>
    {options.deviceLimit > 0 && <Text size="sm">Лимит устройств — {options.deviceLimit}</Text>}
    <Alert color="yellow">Для текущего лимита устройств продление недоступно.</Alert>
  </Stack>;

  const shortfall = selected.price - balance;
  return <Stack>
    <Radio.Group label="Период" value={String(selected.days)} onChange={value => {
      const option = options.options.find(item => String(item.days) === value);
      if (option) onDays(option.days);
    }}>
      <Group mt="xs" gap="md">
        {options.options.map(option => <Radio key={option.days} value={String(option.days)} label={option.days + ' дней'} />)}
      </Group>
    </Radio.Group>
    <Stack gap="xs">
      <Text size="sm">Лимит устройств — {options.deviceLimit}</Text>
      <Text size="sm">{options.estimated ? 'Примерный срок до' : 'Новый срок до'} {date.format(new Date(selected.expiresAt))}</Text>
      <Text size="sm">Баланс — {money.format(balance)}</Text>
      <Text fw={600}>Стоимость — {money.format(selected.price)}</Text>
    </Stack>
    {shortfall > 0 ? (
      <>
        <Text size="sm">Не хватает {money.format(shortfall)}</Text>
        <Button onClick={() => topUp(selected.days, shortfall)}>Пополнить баланс</Button>
      </>
    ) : <Button onClick={() => confirm(options, selected.days, selected.price)}>Подтвердить продление</Button>}
  </Stack>;
}

function OperationStatus({ operation, balance, refreshTerms, topUp }: {
  operation: RenewalOperation;
  balance: number;
  refreshTerms: () => void;
  topUp: (amount: number) => void;
}) {
  const expiry = operation.expiresAt && date.format(new Date(operation.expiresAt));
  const shortfall = operation.price - balance;
  switch (operation.status) {
    case 'TERMS_CHANGED':
      return <>
        <Alert color="yellow" role="status">Условия продления изменились. Проверьте новый расчёт.</Alert>
        <Button onClick={refreshTerms}>Обновить условия</Button>
      </>;
    case 'INSUFFICIENT_BALANCE':
      return <>
        <Alert color="yellow" role="status">{shortfall > 0
          ? 'Не хватает ' + money.format(shortfall) : 'Проверьте актуальные условия и подтвердите продление ещё раз.'}</Alert>
        {shortfall > 0 ? <Button onClick={() => topUp(shortfall)}>Пополнить баланс</Button>
          : <Button onClick={refreshTerms}>Обновить условия</Button>}
      </>;
    case 'MANUAL_REVIEW':
      return <Alert color="yellow" role="status" title="Операция требует проверки">
        <Stack gap="xs">
          <Text size="sm">{operation.charged ? 'Средства списаны.' : 'Списания не было.'}</Text>
          {expiry && <Text size="sm">Подписка действует до {expiry}.</Text>}
          <Text size="sm" style={{ overflowWrap: 'anywhere' }}>Номер операции {operation.id}</Text>
          {operation.supportUrl && <Text component="a" href={operation.supportUrl} size="sm" td="underline">Написать в поддержку</Text>}
        </Stack>
      </Alert>;
    case 'APPLIED':
    case 'COMPLETED':
      return <Alert color="teal" role="status" title="Подписка продлена">
        {expiry && <>Новый срок действия до {expiry}.</>}
      </Alert>;
    default:
      return <Alert role="status" title="Продление обрабатывается">
        {operation.charged ? 'Средства списаны. Ожидаем подтверждение нового срока.' : 'Проверяем условия продления.'}
      </Alert>;
  }
}

import { Alert, Button, Group, Loader, Modal, NumberInput, Select, Stack, Text } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import {
  getGetDashboardQueryKey, useCreateTopUp, useGetTopUp, useGetTopUpOptions,
  type TopUp, type TopUpOptions, type TopUpRequest,
} from './api/generated/configShop';
import { ErrorAlert } from './auth';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });
const statusText: Record<TopUp['state'], string> = {
  CREATING: 'Готовим ссылку на оплату.',
  PENDING: 'Ожидаем подтверждение от Platega. Баланс обновится после зачисления.',
  CONFIRMED: 'Платёж зачислен.',
  CANCELED: 'Платёж отменён или срок оплаты истёк.',
  FAILED: 'Не удалось создать платёж. Можно начать заново.',
  MANUAL_REVIEW: 'Платёж требует проверки. Если вы уже оплатили, сохраните номер заявки для поддержки.',
};

export function TopUpAction() {
  const [opened, setOpened] = useState(false);
  const [params, setParams] = useSearchParams();
  const id = params.get('topUp');

  function showStatus(nextId?: string) {
    setParams(current => {
      const next = new URLSearchParams(current);
      if (nextId) next.set('topUp', nextId);
      else next.delete('topUp');
      return next;
    }, { replace: true });
  }

  return <Stack gap="sm" mt="sm" align="flex-start">
    {id && (uuid.test(id)
      ? <TopUpStatus key={id} id={id} dismiss={() => showStatus()} />
      : <Alert color="red">Номер платежа недействителен.</Alert>)}
    <Button variant="light" onClick={() => setOpened(true)}>Пополнить</Button>
    <Modal opened={opened} onClose={() => setOpened(false)} title="Пополнить баланс" centered>
      {opened && <TopUpFormLoader onCreated={payment => {
        showStatus(payment.id);
        setOpened(false);
        if (payment.state === 'PENDING' && payment.redirect) window.location.assign(payment.redirect);
      }} />}
    </Modal>
  </Stack>;
}

function TopUpFormLoader({ onCreated }: { onCreated: (payment: TopUp) => void }) {
  const options = useGetTopUpOptions();
  if (options.isPending) return <Loader aria-label="Загружаем способы оплаты" size="sm" />;
  if (!options.data) return <Stack align="flex-start">
    <ErrorAlert error={options.error} />
    <Button variant="light" loading={options.isFetching} onClick={() => void options.refetch()}>Повторить</Button>
  </Stack>;
  return <TopUpForm options={options.data} onCreated={onCreated} />;
}

function TopUpForm({ options, onCreated }: { options: TopUpOptions; onCreated: (payment: TopUp) => void }) {
  const [amount, setAmount] = useState<number | string>('');
  const [method, setMethod] = useState(String(options.defaultMethod));
  const attempt = useRef<TopUpRequest | null>(null);
  const creation = useCreateTopUp();
  const valid = typeof amount === 'number' && Number.isSafeInteger(amount)
    && amount >= options.minimum && amount <= options.maximum;

  async function submit() {
    if (!valid || creation.isPending) return;
    const paymentMethod = Number(method);
    if (!attempt.current || attempt.current.amount !== amount || attempt.current.paymentMethod !== paymentMethod) {
      attempt.current = { id: crypto.randomUUID(), amount, paymentMethod };
    }
    try {
      onCreated(await creation.mutateAsync({ data: attempt.current }));
    } catch {
      // The mutation retains its error and the form retains the same attempt ID for retry.
    }
  }

  return <form onSubmit={event => { event.preventDefault(); void submit(); }}>
    <Stack>
      <NumberInput label="Сумма" description={`От ${money.format(options.minimum)} до ${money.format(options.maximum)}`}
        value={amount} onChange={setAmount} min={options.minimum} max={options.maximum}
        allowDecimal={false} allowNegative={false} decimalScale={0} suffix=" ₽"
        disabled={creation.isPending} />
      <Group gap="xs">
        {options.presets.map(preset => <Button type="button" key={preset} size="xs"
          variant={preset === amount ? 'filled' : 'light'} aria-pressed={preset === amount}
          disabled={creation.isPending} onClick={() => setAmount(preset)}>{money.format(preset)}</Button>)}
      </Group>
      <Select label="Способ оплаты" value={method} allowDeselect={false}
        data={options.methods.map(({ code, label }) => ({ value: String(code), label }))}
        disabled={creation.isPending}
        onChange={value => { if (value) setMethod(value); }} />
      {creation.error && <ErrorAlert error={creation.error} />}
      <Button type="submit" disabled={!valid} loading={creation.isPending}>Продолжить пополнение</Button>
    </Stack>
  </form>;
}

function TopUpStatus({ id, dismiss }: { id: string; dismiss: () => void }) {
  const client = useQueryClient();
  const payment = useGetTopUp(id, { query: {
    refetchInterval: query => {
      switch (query.state.data?.state) {
        case 'CREATING':
        case 'PENDING': return 3000;
        case 'CANCELED':
        case 'MANUAL_REVIEW': return 15000;
        default: return false;
      }
    },
  } });
  const state = payment.data?.state;

  useEffect(() => {
    if (state === 'CONFIRMED') void client.invalidateQueries({ queryKey: getGetDashboardQueryKey() });
  }, [client, state]);

  if (payment.isPending) return <Loader aria-label="Проверяем платёж" size="sm" />;
  if (!payment.data) return <Stack gap="xs">
    <ErrorAlert error={payment.error} />
    <Group gap="xs">
      <Button size="xs" variant="light" loading={payment.isFetching} onClick={() => void payment.refetch()}>Повторить</Button>
      <Button size="xs" variant="subtle" onClick={dismiss}>Скрыть</Button>
    </Group>
  </Stack>;
  const { amount, redirect } = payment.data;
  return <Alert title={money.format(amount)} color={state === 'CONFIRMED' ? 'teal' : 'blue'}
    withCloseButton closeButtonLabel="Скрыть статус платежа" onClose={dismiss}>
    <Text size="sm" role="status">{statusText[payment.data.state]}</Text>
    {state === 'MANUAL_REVIEW' && <Text size="xs" mt="xs" style={{ overflowWrap: 'anywhere' }}>{id}</Text>}
    {state === 'PENDING' && redirect && <Button component="a" href={redirect} size="xs" variant="light" mt="xs">
      Перейти к оплате
    </Button>}
  </Alert>;
}

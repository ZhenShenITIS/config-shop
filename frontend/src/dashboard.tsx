import { Alert, Badge, Button, Group, Loader, Paper, Progress, SegmentedControl, Stack, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { ArrowDownToLine, Copy, LogOut, Radio, Send, Wallet } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import {
  logout, useGetDashboard, useGetSubscription, useIssueTelegramCode,
  type Dashboard,
} from './api/generated/configShop';
import { ApiError, setSession, withSessionLock } from './api/http';
import { ErrorAlert } from './auth';

const rubles = new Intl.NumberFormat('ru-RU', {
  style: 'currency', currency: 'RUB', maximumFractionDigits: 0,
});
const traffic = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 });
const date = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeStyle: 'short' });

async function copy(value: string, success: string) {
  try {
    await navigator.clipboard.writeText(value);
    notifications.show({ message: success, color: 'teal' });
  } catch {
    notifications.show({ message: 'Не удалось скопировать. Проверьте доступ к буферу обмена.', color: 'red' });
  }
}

export function DashboardPage() {
  const dashboard = useGetDashboard();

  if (dashboard.isPending) return <Loader aria-label="Загружаем кабинет" />;
  if (!dashboard.data) return <Stack align="flex-start">
    <ErrorAlert error={dashboard.error} />
    <Button variant="light" onClick={() => void dashboard.refetch()} loading={dashboard.isFetching}>Повторить</Button>
  </Stack>;

  return <DashboardView key={dashboard.data.accountId} account={dashboard.data} />;
}

function DashboardView({ account }: { account: Dashboard }) {
  const [selected, setSelected] = useState<string>();
  const [leaving, setLeaving] = useState(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const subscription = account.subscriptions.find(({ id }) => id === selected) ?? account.subscriptions[0];

  async function signOut() {
    setLeaving(true);
    try {
      await withSessionLock(async () => {
        await logout({ headers: { 'X-CSRF-Protection': '1' } });
        setSession();
        queryClient.clear();
      });
      navigate('/', { replace: true });
    } catch (error) {
      notifications.show({ color: 'red', message: error instanceof ApiError ? error.message : 'Не удалось выйти.' });
    } finally {
      setLeaving(false);
    }
  }

  return <Stack gap="xl">
    <Group justify="space-between" align="flex-end">
      <div>
        <Title order={1}>Личный кабинет</Title>
        <Text c="dimmed" className="account-email">{account.email}</Text>
      </div>
      <Button variant="subtle" leftSection={<LogOut size={17} />} onClick={() => void signOut()} loading={leaving}>Выйти</Button>
    </Group>
    <div className="dashboard-grid">
      <Paper className="dashboard-card balance-card" withBorder radius="lg">
        <span className="tile-icon"><Wallet size={20} /></span>
        <Text mt="md" c="dimmed">Баланс</Text>
        <Text className="balance-value">{rubles.format(account.balance)}</Text>
      </Paper>
      <Paper className="dashboard-card" withBorder radius="lg">
        <span className="tile-icon"><Send size={19} /></span>
        <Title order={2} size="h4" mt="md">Telegram</Title>
        {account.telegramLinked
          ? <Badge color="teal" variant="light" mt="xs">Подключён</Badge>
          : <Button component={Link} to="/link-telegram.html" variant="light" mt="sm">Привязать Telegram</Button>}
      </Paper>
    </div>
    <Paper className="dashboard-card subscriptions-card" withBorder radius="lg">
      <Stack gap="lg">
        <Group gap="sm"><span className="tile-icon"><Radio size={20} /></span><Title order={2} size="h3">Подписки</Title></Group>
        {account.subscriptions.length > 1 && <SegmentedControl
          aria-label="Выбрать подписку"
          value={subscription?.id}
          onChange={setSelected}
          data={account.subscriptions.map(({ id }, index) => ({ value: id, label: 'Подписка ' + (index + 1) }))}
        />}
        {subscription
          ? <SubscriptionView key={subscription.id} id={subscription.id} />
          : <Text c="dimmed">У вас пока нет подписок.</Text>}
      </Stack>
    </Paper>
  </Stack>;
}

function SubscriptionView({ id }: { id: string }) {
  const subscription = useGetSubscription(id);
  if (subscription.isPending) return <Loader aria-label="Загружаем подписку" size="sm" />;
  if (!subscription.data) return <Stack align="flex-start">
    <ErrorAlert error={subscription.error} />
    <Button variant="light" onClick={() => void subscription.refetch()} loading={subscription.isFetching}>Повторить</Button>
  </Stack>;

  const { active, expiresAt, usedTrafficGb, trafficLimitGb, connectionUrl } = subscription.data;
  const link = connectionUrl?.startsWith('happ://crypt4/') ? connectionUrl : null;

  return <Stack gap="lg">
    <div className="subscription-stats">
      <div>
        <Text size="sm" c="dimmed">Срок действия</Text>
        <Text fw={600}>{date.format(new Date(expiresAt))}</Text>
        <Badge color={active ? 'teal' : 'gray'} variant="light" mt="xs">{active ? 'Действует' : 'Истекла'}</Badge>
      </div>
      <div>
        <Text size="sm" c="dimmed">Трафик</Text>
        <Text fw={600}>{traffic.format(usedTrafficGb)} ГБ {trafficLimitGb > 0
          ? 'из ' + traffic.format(trafficLimitGb) + ' ГБ' : '· безлимит'}</Text>
        {trafficLimitGb > 0 && <Progress mt="sm" value={Math.min(100, 100 * usedTrafficGb / trafficLimitGb)}
          aria-label="Использовано трафика" />}
      </div>
    </div>
    {link ? (
      <div className="connection-panel">
        <Stack gap="sm">
          <Title order={3} size="h4">Подключение</Title>
          <Text size="sm" c="dimmed">Установите Happ и откройте подписку. Ссылку также можно скопировать и импортировать в Happ из буфера обмена.</Text>
          <Group gap="sm">
            <Button component="a" href={link} leftSection={<ArrowDownToLine size={17} />}>Открыть в Happ</Button>
            <Button variant="light" leftSection={<Copy size={16} />} onClick={() => void copy(link, 'Ссылка скопирована')}>Скопировать ссылку</Button>
          </Group>
        </Stack>
      </div>
    ) : <Alert color="yellow">Ссылка для подключения пока недоступна.</Alert>}
  </Stack>;
}

export function TelegramPage() {
  const dashboard = useGetDashboard();
  const issue = useIssueTelegramCode();
  const expiresAt = issue.data?.expiresAt;
  const [expiredCode, setExpiredCode] = useState<string>();
  const [checking, setChecking] = useState(false);
  const navigate = useNavigate();
  const code = expiresAt && expiresAt !== expiredCode ? issue.data?.code : undefined;
  const linked = dashboard.data?.telegramLinked ||
    (issue.error instanceof ApiError && issue.error.code === 'telegram_already_linked');

  useEffect(() => {
    if (!expiresAt) return;
    const timer = window.setTimeout(() => setExpiredCode(expiresAt), Math.max(0, Date.parse(expiresAt) - Date.now()));
    return () => window.clearTimeout(timer);
  }, [expiresAt]);

  async function checkLink() {
    setChecking(true);
    try {
      const result = await dashboard.refetch();
      if (result.data?.telegramLinked) {
        navigate('/dashboard', { replace: true });
      } else if (!result.error) {
        notifications.show({ message: 'Привязка ещё не завершена. Проверьте код в боте.', color: 'yellow' });
      }
    } finally {
      setChecking(false);
    }
  }

  return <Paper className="auth-card" radius="lg" withBorder component="section">
    <Stack gap="lg">
      <Title order={1} size="h2">Привязать Telegram</Title>
      {linked ? <Alert color="teal">Telegram уже подключён к этому аккаунту.</Alert> : (
        <>
          <Text>В личном чате с ботом выберите «Подключить сайт» и введите код. Код действует 5 минут.</Text>
          <ErrorAlert error={issue.error ?? dashboard.error} />
          {code && <div className="link-code">
            <output aria-label="Код привязки" aria-live="polite">{code}</output>
            <Button variant="subtle" leftSection={<Copy size={16} />} onClick={() => void copy(code, 'Код скопирован')}>Скопировать</Button>
          </div>}
          {expiresAt === expiredCode && expiresAt && <Alert color="yellow">Срок кода истёк. Получите новый.</Alert>}
          <Button onClick={() => issue.mutate()} loading={issue.isPending}>{code ? 'Получить новый код' : 'Получить код'}</Button>
          <Button variant="light" onClick={() => void checkLink()} loading={checking}>Я ввёл код</Button>
        </>
      )}
      <Link to="/dashboard">Вернуться в кабинет</Link>
    </Stack>
  </Paper>;
}

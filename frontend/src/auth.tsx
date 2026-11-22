import { Alert, Button, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useQueryClient } from '@tanstack/react-query';
import { Mail } from 'lucide-react';
import { type ReactNode, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import {
  useConfirmEmail, useForgotPassword, useLogin, useRegister, useResendConfirmation, useResetPassword,
} from './api/generated/configShop';
import { setSession, withSessionLock } from './api/http';

const validateEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
  ? null : 'Введите корректный адрес почты.';

const validatePassword = (value: string) => value.length < 12 || new TextEncoder().encode(value).length > 72
  ? 'Введите пароль от 12 символов, до 72 байт.' : null;

function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return <Paper className="auth-card" withBorder radius="lg" component="section">
    <Stack gap="lg">
      <Title order={1} size="h2">{title}</Title>
      {children}
    </Stack>
  </Paper>;
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;
  return <Alert color="red" role="alert">
    {error instanceof Error ? error.message : 'Не удалось выполнить запрос.'}
  </Alert>;
}

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const [search] = useSearchParams();
  const isRegister = mode === 'register';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const nextPage = search.get('next') === '/link-telegram.html' ? '/link-telegram.html' : '/dashboard';
  const login = useLogin();
  const register = useRegister();
  const form = useForm({
    initialValues: { email: '', password: '', referralCode: search.get('ref') ?? '' },
    validate: {
      email: validateEmail,
      password: isRegister ? validatePassword : (value) => value ? null : 'Введите пароль.',
    },
  });
  const mutation = isRegister ? register : login;
  const resendState = { email: form.values.email.trim() };

  if (register.data) {
    return <AuthCard title="Проверьте почту">
      <Alert icon={<Mail size={18} />} color="teal">{register.data.message}</Alert>
      <Link to="/resend-confirmation" state={resendState}>Отправить письмо повторно</Link>
      <Link to="/login">Перейти ко входу</Link>
    </AuthCard>;
  }

  return <AuthCard title={isRegister ? 'Создать аккаунт' : 'Вход в аккаунт'}>
    <form onSubmit={form.onSubmit(async ({ email, password, referralCode }) => {
      try {
        if (isRegister) {
          await register.mutateAsync({ data: { email: email.trim(), password, referralCode: referralCode.trim() || undefined } });
        } else {
          await withSessionLock(async () => {
            const { accessToken } = await login.mutateAsync({ data: { email: email.trim(), password } });
            queryClient.clear();
            setSession(accessToken);
          });
          navigate(nextPage, { replace: true });
        }
      } catch {
        // The mutation supplies the message shown below the fields.
      }
    })}>
      <Stack>
        <TextInput label="Почта" type="email" autoComplete="username" maxLength={320} required
          {...form.getInputProps('email')} />
        <PasswordInput label="Пароль" autoComplete={isRegister ? 'new-password' : 'current-password'}
          maxLength={72} minLength={isRegister ? 12 : undefined} required {...form.getInputProps('password')} />
        {isRegister && <TextInput label="Реферальный код" maxLength={255} {...form.getInputProps('referralCode')} />}
        <ErrorAlert error={mutation.error} />
        <Button type="submit" loading={mutation.isPending}>{isRegister ? 'Зарегистрироваться' : 'Войти'}</Button>
      </Stack>
    </form>
    {isRegister ? (
      <>
        <Text size="sm">Уже есть аккаунт? <Link to="/login">Войти</Link></Text>
        {register.isError && <Link to="/resend-confirmation" state={resendState}>Запросить письмо подтверждения</Link>}
      </>
    ) : (
      <div className="auth-links">
        <Link to="/forgot-password">Забыли пароль?</Link>
        <Link to="/resend-confirmation" state={resendState}>Запросить письмо подтверждения</Link>
        <Link to="/register">Создать аккаунт</Link>
      </div>
    )}
  </AuthCard>;
}

export function MailPage({ mode }: { mode: 'confirm' | 'password' }) {
  const { state } = useLocation();
  const resend = useResendConfirmation();
  const forgot = useForgotPassword();
  const action = mode === 'confirm' ? resend : forgot;
  const form = useForm({ initialValues: { email: typeof state?.email === 'string' ? state.email : '' },
    validate: { email: validateEmail } });

  return <AuthCard title={mode === 'confirm' ? 'Письмо подтверждения' : 'Восстановить пароль'}>
    {action.data ? <Alert icon={<Mail size={18} />} color="teal">{action.data.message}</Alert> : (
      <form onSubmit={form.onSubmit(({ email }) => action.mutate({ data: { email: email.trim() } }))}>
        <Stack>
          <TextInput label="Почта" type="email" autoComplete="username" maxLength={320} required
            {...form.getInputProps('email')} />
          <ErrorAlert error={action.error} />
          <Button type="submit" loading={action.isPending}>Отправить письмо</Button>
        </Stack>
      </form>
    )}
    <Link to="/login">Вернуться ко входу</Link>
  </AuthCard>;
}

function useEmailToken() {
  return useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') ?? '')[0];
}

function clearEmailToken() {
  window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
}

export function ConfirmPage() {
  const token = useEmailToken();
  const confirm = useConfirmEmail({ mutation: { onSuccess: clearEmailToken } });
  return <AuthCard title="Подтвердить почту">
    {confirm.data ? (
      <>
        <Alert color={confirm.data.trialCreated ? 'teal' : 'yellow'}>{confirm.data.message}</Alert>
        {!confirm.data.trialCreated && <Text size="sm">Номер аккаунта для поддержки <code>{confirm.data.accountId}</code></Text>}
        <Button component={Link} to="/login">Перейти ко входу</Button>
      </>
    ) : (
      <>
        <ErrorAlert error={confirm.error} />
        {token ? <Button onClick={() => confirm.mutate({ data: { token } })} loading={confirm.isPending}>Подтвердить</Button>
          : <Alert color="yellow">Откройте ссылку из письма или запросите новую.</Alert>}
        <Link to="/resend-confirmation">Запросить новое письмо</Link>
      </>
    )}
  </AuthCard>;
}

export function ResetPage() {
  const token = useEmailToken();
  const queryClient = useQueryClient();
  const reset = useResetPassword({
    mutation: { onSuccess: () => { clearEmailToken(); setSession(); queryClient.clear(); } },
  });
  const [done, setDone] = useState(false);
  const form = useForm({ initialValues: { password: '' }, validate: { password: validatePassword } });

  return <AuthCard title="Установить новый пароль">
    {done ? (
      <>
        <Alert color="teal">Пароль изменён.</Alert>
        <Button component={Link} to="/login">Войти с новым паролем</Button>
      </>
    ) : token ? (
      <form onSubmit={form.onSubmit(async ({ password }) => {
        try {
          await withSessionLock(() => reset.mutateAsync({ data: { token, password } }));
          setDone(true);
        } catch {
          // The mutation supplies the message shown below the field.
        }
      })}>
        <Stack>
          <PasswordInput label="Новый пароль" autoComplete="new-password" minLength={12} maxLength={72} required
            {...form.getInputProps('password')} />
          <ErrorAlert error={reset.error} />
          <Button type="submit" loading={reset.isPending}>Сохранить пароль</Button>
        </Stack>
      </form>
    ) : <Alert color="yellow">Откройте ссылку из письма или запросите новую.</Alert>}
    {!done && <Link to="/forgot-password">Запросить новую ссылку</Link>}
  </AuthCard>;
}

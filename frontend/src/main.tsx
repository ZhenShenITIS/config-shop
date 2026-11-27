import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import './styles.css';

import { Alert, Button, Group, Loader, MantineProvider, createTheme } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { StrictMode, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { restoreSession, useSession } from './api/http';
import { AuthPage, ConfirmPage, MailPage, ResetPage } from './auth';
import { DashboardPage, TelegramPage } from './dashboard';

const theme = createTheme({
  primaryColor: 'teal',
  primaryShade: 8,
  fontFamily: 'system-ui, sans-serif',
  headings: { fontFamily: 'system-ui, sans-serif' },
  defaultRadius: 'md',
});

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, staleTime: 15_000 } },
});

function Layout() {
  const session = useSession();
  useEffect(() => { void restoreSession().catch(() => undefined); }, []);

  return (
    <>
      <header className="site-header">
        <div className="site-inner header-inner">
          <Link className="wordmark" to="/">
            <span className="brand-icon"><ShieldCheck size={20} /></span>
            Crump
          </Link>
          <Button component={Link} to={session === 'authenticated' ? '/dashboard' : '/login'} variant="subtle">
            {session === 'authenticated' ? 'Кабинет' : 'Войти'}
          </Button>
        </div>
      </header>
      <main className="site-inner site-main"><Outlet /></main>
    </>
  );
}

function Home() {
  const signedIn = useSession() === 'authenticated';
  return (
    <section className="hero">
      <div className="hero-copy">
        <h1>Подключайтесь<br />через Crump</h1>
        <p>Оформите пробную подписку и управляйте подключением в личном кабинете.</p>
        <Group gap="sm">
          <Button className="hero-button" size="md" component={Link}
            to={signedIn ? '/dashboard' : '/register'} rightSection={<ArrowRight size={17} />}>
            {signedIn ? 'Открыть кабинет' : 'Зарегистрироваться'}
          </Button>
          {!signedIn && <Link className="hero-link" to="/login">У меня есть аккаунт</Link>}
        </Group>
      </div>
      <div className="hero-visual" aria-hidden="true">
        <div className="hero-orbit orbit-outer" />
        <div className="hero-orbit orbit-inner" />
        <div className="hero-symbol"><ShieldCheck strokeWidth={1.2} /></div>
      </div>
    </section>
  );
}

function RequireSession() {
  const session = useSession();
  const { pathname, search } = useLocation();
  if (session === 'loading') return <Loader aria-label="Проверяем сеанс" />;
  if (session === 'unavailable') {
    return <Alert color="red" title="Не удалось проверить сеанс">
      <Button variant="subtle" onClick={() => void restoreSession().catch(() => undefined)}>Повторить</Button>
    </Alert>;
  }
  if (session === 'anonymous') {
    const login = new URLSearchParams();
    if (pathname === '/link-telegram.html') login.set('next', pathname);
    const topUp = new URLSearchParams(search).get('topUp');
    if (pathname === '/dashboard' && topUp) login.set('topUp', topUp);
    return <Navigate to={{ pathname: '/login', search: login.toString() }} replace />;
  }
  return <Outlet />;
}

function NotFound() {
  return <section><h1>Страница не найдена</h1><Link to="/">На главную</Link></section>;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider theme={theme}>
      <Notifications position="bottom-right" />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Home />} />
              <Route path="login" element={<AuthPage key="login" mode="login" />} />
              <Route path="register" element={<AuthPage key="register" mode="register" />} />
              <Route path="confirm-email" element={<ConfirmPage />} />
              <Route path="resend-confirmation" element={<MailPage key="confirm" mode="confirm" />} />
              <Route path="forgot-password" element={<MailPage key="password" mode="password" />} />
              <Route path="reset-password" element={<ResetPage />} />
              <Route element={<RequireSession />}>
                <Route path="dashboard" element={<DashboardPage />} />
                <Route path="link-telegram.html" element={<TelegramPage />} />
              </Route>
              <Route path="*" element={<NotFound />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);

import { FormEvent, useState } from 'react';
import { MessageCircle } from 'lucide-react';
import { api } from '../../shared/api/client';
import { useAuthStore } from '../../store/authStore';
import type { ApiResponse, CurrentUser } from '../../shared/types';

type Mode = 'login' | 'register' | 'forgot' | 'reset';

export function AuthView() {
  const resetToken = new URLSearchParams(window.location.search).get('token') ?? '';
  const isResetUrl = window.location.pathname === '/reset-password' && Boolean(resetToken);
  const [mode, setMode] = useState<Mode>(isResetUrl ? 'reset' : 'login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const setAuth = useAuthStore((s) => s.setAuth);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      if (mode === 'forgot') {
        const response = await api.post<ApiResponse<{ message: string }>>('/auth/forgot-password', { email });
        setMessage(response.data.data.message);
        return;
      }

      if (mode === 'reset') {
        const response = await api.post<ApiResponse<{ message: string }>>('/auth/reset-password', {
          token: resetToken,
          newPassword
        });
        setMessage(response.data.data.message);
        setMode('login');
        setPassword('');
        setNewPassword('');
        window.history.replaceState({}, '', '/');
        return;
      }

      if (mode === 'register') {
        await api.post('/auth/register', { username, email, password });
      }
      const response = await api.post<ApiResponse<{ accessToken: string; user: CurrentUser }>>('/auth/login', { email, password });
      setAuth(response.data.data.accessToken, response.data.data.user);
    } catch (err: any) {
      setError(err.response?.data?.error?.message ?? 'Khong the xu ly yeu cau.');
    } finally {
      setBusy(false);
    }
  }

  function backToLogin() {
    setMode('login');
    setError('');
    setMessage('');
    if (window.location.pathname === '/reset-password') {
      window.history.replaceState({}, '', '/');
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-panel" onSubmit={submit}>
        <div className="brand-row">
          <MessageCircle size={24} />
          <strong>Mini Discord</strong>
        </div>

        {mode !== 'forgot' && mode !== 'reset' && (
          <div className="segmented">
            <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
              Login
            </button>
            <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
              Register
            </button>
          </div>
        )}

        {mode === 'forgot' && <h1 className="auth-title">Forgot password</h1>}
        {mode === 'reset' && <h1 className="auth-title">Reset password</h1>}

        {mode === 'register' && (
          <label>
            Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} maxLength={32} required />
          </label>
        )}

        {mode !== 'reset' && (
          <label>
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
        )}

        {(mode === 'login' || mode === 'register') && (
          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} required />
            {mode === 'login' && (
              <button type="button" className="forgot-link" onClick={() => setMode('forgot')}>
                Forgot password?
              </button>
            )}
          </label>
        )}

        {mode === 'reset' && (
          <label>
            New password
            <input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={8} required />
          </label>
        )}

        {error && <p className="form-error">{error}</p>}
        {message && <p className="form-message">{message}</p>}

        <button className="primary" disabled={busy}>
          {busy ? 'Dang xu ly' : mode === 'login' ? 'Login' : mode === 'register' ? 'Register' : mode === 'forgot' ? 'Send reset link' : 'Reset password'}
        </button>

        {(mode === 'forgot' || mode === 'reset') && (
          <button type="button" className="auth-back" onClick={backToLogin}>
            Back to login
          </button>
        )}
      </form>
    </main>
  );
}

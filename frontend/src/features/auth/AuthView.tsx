import { FormEvent, useState } from 'react';
import { MessageCircle } from 'lucide-react';
import { api } from '../../shared/api/client';
import { useAuthStore } from '../../store/authStore';
import type { ApiResponse, CurrentUser } from '../../shared/types';

type Mode = 'login' | 'register';

export function AuthView() {
  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const setAuth = useAuthStore((s) => s.setAuth);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (mode === 'register') {
        await api.post('/auth/register', { username, email, password });
      }
      const response = await api.post<ApiResponse<{ accessToken: string; user: CurrentUser }>>('/auth/login', { email, password });
      setAuth(response.data.data.accessToken, response.data.data.user);
    } catch (err: any) {
      setError(err.response?.data?.error?.message ?? 'Không thể đăng nhập.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-panel" onSubmit={submit}>
        <div className="brand-row">
          <MessageCircle size={24} />
          <strong>Mini Discord</strong>
        </div>

        <div className="segmented">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
            Login
          </button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
            Register
          </button>
        </div>

        {mode === 'register' && (
          <label>
            Username
            <input value={username} onChange={(event) => setUsername(event.target.value)} minLength={3} maxLength={32} required />
          </label>
        )}

        <label>
          Email
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        </label>

        <label>
          Password
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} required />
        </label>

        {error && <p className="form-error">{error}</p>}

        <button className="primary" disabled={busy}>
          {busy ? 'Đang xử lý' : mode === 'login' ? 'Login' : 'Register'}
        </button>
      </form>
    </main>
  );
}

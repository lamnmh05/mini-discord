import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MessageSquare } from 'lucide-react';
import { AuthView } from '../features/auth/AuthView';
import { ChatShell } from '../features/chat/ChatShell';
import { refreshToken, unwrap, api } from '../shared/api/client';
import { useAuthStore } from '../store/authStore';
import type { CurrentUser } from '../shared/types';

export function App() {
  const { accessToken, setAuth } = useAuthStore();
  const [booted, setBooted] = useState(false);

  useEffect(() => {
    refreshToken().finally(() => setBooted(true));
  }, []);

  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: () => unwrap(api.get<import('../shared/types').ApiResponse<CurrentUser>>('/users/me')),
    enabled: Boolean(accessToken)
  });

  useEffect(() => {
    if (accessToken && meQuery.data) {
      setAuth(accessToken, meQuery.data);
    }
  }, [accessToken, meQuery.data, setAuth]);

  if (!booted) {
    return (
      <main className="boot">
        <MessageSquare size={28} />
      </main>
    );
  }

  return accessToken ? <ChatShell /> : <AuthView />;
}

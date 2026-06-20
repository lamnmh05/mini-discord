import { create } from 'zustand';
import type { CurrentUser } from '../shared/types';

type AuthState = {
  accessToken?: string;
  user?: CurrentUser;
  setAuth: (accessToken: string, user: CurrentUser) => void;
  clear: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  setAuth: (accessToken, user) => set({ accessToken, user }),
  clear: () => set({ accessToken: undefined, user: undefined })
}));

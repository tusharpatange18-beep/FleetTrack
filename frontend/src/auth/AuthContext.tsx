import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setToken, setUnauthorizedHandler } from '../api/client';

/** What every page can read through useAuth(). */
interface AuthState {
  token: string | null;
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

interface Session {
  token: string;
  username: string;
}

// A React "context" shares one value with every component inside <AuthProvider>
const AuthContext = createContext<AuthState | null>(null);
const STORAGE_KEY = 'fleettrack.session';

// Storage can be unavailable (private mode, blocked site data); the app still works, you just
// log in again after a refresh.
function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // storage blocked: ignore, the user just logs in again after a refresh
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Start with the session saved in the browser, if any (survives a page refresh)
  const [session, setSession] = useState<Session | null>(loadSession);

  // Give the API client the current token on every render
  let currentToken: string | null = null;
  let currentUsername: string | null = null;
  if (session) {
    currentToken = session.token;
    currentUsername = session.username;
  }
  setToken(currentToken);

  const logout = useCallback(() => {
    setToken(null);
    saveSession(null);
    setSession(null);
  }, []);

  // When any request gets a 401, log out
  useEffect(() => {
    setUnauthorizedHandler(logout);
  }, [logout]);

  const login = useCallback(async (username: string, password: string) => {
    // Throws (and shows the error on the login page) if the password is wrong
    const response = await api.login(username, password);
    const newSession = { token: response.token, username: username };
    setToken(newSession.token);
    saveSession(newSession);
    setSession(newSession);
  }, []);

  // useMemo: only build a new object when something changed, so pages don't re-render for nothing
  const value = useMemo<AuthState>(() => {
    return { token: currentToken, username: currentUsername, login: login, logout: logout };
  }, [currentToken, currentUsername, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Use this in any page to get the token, the username, login and logout. */
export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return auth;
}

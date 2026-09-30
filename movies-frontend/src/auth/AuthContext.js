import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import api, { setAccessToken } from '../api/axiosConfig';

const TOKEN_KEY = 'movieGold.accessToken';
const AuthContext = createContext(null);

// Reading "exp" lets the UI discard an expired token early.
// This is NOT verification: the payload is readable by anyone, and only the server can trust a token.
const isExpired = (token) => {
    try {
        const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        return payload.exp * 1000 <= Date.now();
    } catch {
        return true;
    }
};

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [ready, setReady] = useState(false);

    const logout = useCallback(() => {
        sessionStorage.removeItem(TOKEN_KEY);
        setAccessToken(null);
        setUser(null);
    }, []);

    const startSession = useCallback(async (token) => {
        sessionStorage.setItem(TOKEN_KEY, token);
        setAccessToken(token);
        const response = await api.get('/api/v1/users/me');
        setUser(response.data);
    }, []);

    // On page load, resume this tab's session if its token is still valid
    useEffect(() => {
        const saved = sessionStorage.getItem(TOKEN_KEY);
        if (saved && !isExpired(saved)) {
            startSession(saved).catch(logout).finally(() => setReady(true));
        } else {
            logout();
            setReady(true);
        }
    }, [startSession, logout]);

    // A request that was sent WITH a token and came back 401 means the token expired or was rejected
    useEffect(() => {
        const id = api.interceptors.response.use(
            (response) => response,
            (error) => {
                if (error.response?.status === 401 && error.config?.headers?.Authorization) {
                    logout();
                }
                return Promise.reject(error);
            });
        return () => api.interceptors.response.eject(id);
    }, [logout]);

    const login = async (username, password) => {
        const response = await api.post('/api/v1/auth/token', { username, password });
        await startSession(response.data.accessToken);
    };

    const register = async (username, password) => {
        await api.post('/api/v1/users', { username, password });
        await login(username, password);
    };

    return (
        <AuthContext.Provider value={{ user, ready, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthProvider as OidcProvider, useAuth as useOidc } from 'react-oidc-context';
import { WebStorageStateStore } from 'oidc-client-ts';
import api, { setAccessToken } from '../api/axiosConfig';

// Users log in on Keycloak's own pages (Authorization Code flow + PKCE): this app never sees a password.
// The OIDC library does the protocol work; this file adapts it to the small useAuth() API the components use.
const oidcConfig = {
    authority: process.env.REACT_APP_OIDC_AUTHORITY,
    client_id: process.env.REACT_APP_OIDC_CLIENT_ID,
    redirect_uri: `${window.location.origin}/`,
    post_logout_redirect_uri: `${window.location.origin}/`,
    scope: 'openid profile',
    // Tokens stay in this tab and are cleared when it closes (the trade-offs are in README.md)
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    // Uses the refresh token to fetch a new access token shortly before the current one expires
    automaticSilentRenew: true,
    // Keycloak returns with ?code=...&state=...; once exchanged for tokens, remove them from the address bar
    onSigninCallback: () => window.history.replaceState({}, document.title, window.location.pathname),
};

// Where to go after Keycloak sends the user back. Set only by our own code, never from a URL parameter
// (taking it from the URL unchecked would be an open redirect).
const RETURN_TO_KEY = 'movieGold.returnTo';
const isInternalPath = (path) => typeof path === 'string' && path.startsWith('/') && !path.startsWith('//');

const AuthContext = createContext(null);

const AppAuthProvider = ({ children }) => {
    const oidc = useOidc();
    const navigate = useNavigate();
    const [user, setUser] = useState(null);
    const [profileLoaded, setProfileLoaded] = useState(false);
    const accessToken = oidc.user?.access_token;

    // Keep axios' Authorization header in step with the current (possibly renewed) token,
    // then ask the API who we are: the API's reading of the token is the one that counts
    useEffect(() => {
        setAccessToken(accessToken ?? null);
        if (!accessToken) {
            setUser(null);
            setProfileLoaded(true);
            return undefined;
        }
        let cancelled = false;
        setProfileLoaded(false);
        api.get('/api/v1/users/me')
            .then((response) => { if (!cancelled) setUser(response.data); })
            .catch(() => { if (!cancelled) setUser(null); })
            .finally(() => { if (!cancelled) setProfileLoaded(true); });
        return () => { cancelled = true; };
    }, [accessToken]);

    // Back from Keycloak: return to the page the user started from (e.g. a movie's reviews)
    useEffect(() => {
        if (!oidc.isAuthenticated) return;
        const returnTo = sessionStorage.getItem(RETURN_TO_KEY);
        if (returnTo) {
            sessionStorage.removeItem(RETURN_TO_KEY);
            if (isInternalPath(returnTo)) navigate(returnTo, { replace: true });
        }
    }, [oidc.isAuthenticated, navigate]);

    // A request that carried a token and got 401 means the token was rejected: drop the local session
    useEffect(() => {
        const id = api.interceptors.response.use(
            (response) => response,
            (error) => {
                if (error.response?.status === 401 && error.config?.headers?.Authorization) {
                    oidc.removeUser();
                }
                return Promise.reject(error);
            });
        return () => api.interceptors.response.eject(id);
    }, [oidc]);

    const login = useCallback((returnTo) => {
        sessionStorage.setItem(RETURN_TO_KEY, returnTo ?? '/');
        return oidc.signinRedirect();
    }, [oidc]);

    const register = useCallback((returnTo) => {
        sessionStorage.setItem(RETURN_TO_KEY, returnTo ?? '/');
        // prompt=create asks Keycloak to show its sign-up form instead of the login form
        return oidc.signinRedirect({ prompt: 'create' });
    }, [oidc]);

    const logout = useCallback(() => {
        setAccessToken(null);
        // Ends the Keycloak session as well (single sign-out), then comes back to the home page
        return oidc.signoutRedirect();
    }, [oidc]);

    const ready = !oidc.isLoading && profileLoaded;

    return (
        <AuthContext.Provider value={{ user, ready, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export const AuthProvider = ({ children }) => (
    <OidcProvider {...oidcConfig}>
        <AppAuthProvider>{children}</AppAuthProvider>
    </OidcProvider>
);

export const useAuth = () => useContext(AuthContext);

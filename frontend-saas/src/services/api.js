import axios from 'axios';

const baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const TOKEN_KEY = 'jwt_token';
const REFRESH_KEY = 'refresh_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const hasSession = () => Boolean(getToken());

export function saveSession(auth) {
    localStorage.setItem(TOKEN_KEY, auth.token);
    if (auth.refreshToken) localStorage.setItem(REFRESH_KEY, auth.refreshToken);
}

export function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
}

/** Mensaje de error legible tomado de la respuesta del backend (sobre {message,data}). */
export function apiError(err, fallback = 'Ocurrió un error inesperado') {
    if (err?.response?.data?.message) return err.response.data.message;
    if (err?.request && !err.response) return 'Error de conexión con el servidor';
    return fallback;
}

const api = axios.create({ baseURL });

const isAuthUrl = (url) => typeof url === 'string' && url.includes('/auth/');

function forceLogout() {
    clearSession();
    const publicPaths = ['/login', '/register', '/privacidad'];
    if (!publicPaths.includes(window.location.pathname)) {
        window.location.assign('/login');
    }
}

// Antes de enviar: adjunta el access token (salvo en /auth/*)
api.interceptors.request.use((config) => {
    if (isAuthUrl(config.url)) {
        if (config.headers) delete config.headers.Authorization;
        return config;
    }
    const token = getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Un solo refresco a la vez: las demás peticiones esperan la misma promesa (cola).
let refreshPromise = null;

function refreshTokens() {
    if (!refreshPromise) {
        const refreshToken = localStorage.getItem(REFRESH_KEY);
        if (!refreshToken) return Promise.reject(new Error('Sin refresh token'));
        // Se usa axios "limpio" para no pasar por estos interceptores
        refreshPromise = axios
            .post(`${baseURL}/auth/refresh`, { refreshToken })
            .then((res) => {
                const auth = res.data.data;
                saveSession(auth);
                return auth.token;
            })
            .finally(() => {
                refreshPromise = null;
            });
    }
    return refreshPromise;
}

api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config;
        if (error.response?.status === 401 && original && !original._retry && !isAuthUrl(original.url)) {
            original._retry = true;
            try {
                const token = await refreshTokens();
                original.headers.Authorization = `Bearer ${token}`;
                return api(original);
            } catch {
                forceLogout();
            }
        }
        return Promise.reject(error);
    }
);

export default api;

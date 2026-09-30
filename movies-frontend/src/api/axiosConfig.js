import axios from 'axios';

const api = axios.create({
    baseURL: process.env.REACT_APP_API_BASE_URL
});

// A module-level variable: every request can read it, and changing it doesn't re-render anything
let accessToken = null;

export const setAccessToken = (token) => {
    accessToken = token;
};

// Runs before every request made with `api`
api.interceptors.request.use((config) => {
    if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
});

export default api;
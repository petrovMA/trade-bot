import axios, { AxiosInstance, AxiosError } from 'axios';

// API base URL for trade-bot deployment
const API_BASE_URL = '/trade-bot/api';

export const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 300000,
  // Enable credentials for session-based auth (shared with Python project)
  withCredentials: true,
});

// Request interceptor
api.interceptors.request.use(
  (config) => {
    console.log(`API Request: ${config.method?.toUpperCase()} ${config.url}`);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error: AxiosError) => {
    console.error('API Error:', error.response?.data || error.message);

    if (error.response?.status === 401) {
      // Unauthorized - redirect to Python project's login
      console.error('Authentication required - redirecting to login');
      window.location.href = '/auth/login?next=' + encodeURIComponent(window.location.pathname);
    } else if (error.response?.status === 404) {
      console.error('Resource not found');
    } else if (error.response?.status === 500) {
      console.error('Server error');
    }

    return Promise.reject(error);
  }
);

export default api;

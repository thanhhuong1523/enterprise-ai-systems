import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

let accessToken: string | null = null;
let hasSession = false;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

export const setHasSession = (val: boolean) => {
  hasSession = val;
};

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

apiClient.interceptors.request.use(
  (config) => {
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Check if error is 401 and we haven't retried yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      // If we are already on the login page, don't try to refresh
      if (window.location.pathname.endsWith('/login')) {
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => {
            return Promise.reject(err);
          });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      if (hasSession) {
        try {
          // Sử dụng instance axios sạch để tránh các interceptor khác can thiệp
          const response = await axios.post(
            `${API_BASE_URL}/api/v1/auth/refresh`,
            {},
            { withCredentials: true }
          );
          const { accessToken: newAccessToken } = response.data.data;

          setAccessToken(newAccessToken);

          apiClient.defaults.headers.common['Authorization'] = `Bearer ${newAccessToken}`;
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

          processQueue(null, newAccessToken);
          isRefreshing = false;

          return apiClient(originalRequest);
        } catch (refreshError) {
          processQueue(refreshError, null);
          isRefreshing = false;

          setAccessToken(null);
          setHasSession(false);

          window.location.href = '/login';
          return Promise.reject(refreshError);
        }
      } else {
        setAccessToken(null);
        setHasSession(false);
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

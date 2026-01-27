export const BASE_URL = (import.meta.env.VITE_REACT_APP_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const SSE_URL = BASE_URL + "/quote/stream";

// Helper to get API base URL with /api/v1 prefix if not already present
export const getApiBaseUrl = () => {
    if (BASE_URL.includes('/api/v1')) {
        return BASE_URL;
    }
    return BASE_URL + '/api/v1';
};

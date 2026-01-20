export const BASE_URL = import.meta.env.VITE_REACT_APP_API_BASE_URL.replace(/\/$/, '');
export const SSE_URL = BASE_URL + "/quote/stream";

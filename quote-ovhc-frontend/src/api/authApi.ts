import axios from 'axios';
import { getApiBaseUrl } from '../constants/constants';

class ApiService {
  private client: any;

  constructor() {
    this.client = axios.create({
      baseURL: getApiBaseUrl(),
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Add response interceptor to handle 401 errors
    this.client.interceptors.response.use(
      (response: any) => response,
      (error: any) => {
        if (error.response?.status === 401) {
          console.log('🔒 401 Unauthorized - token expired or invalid');
          // Clear token and user data
          this.clearToken();
          // Trigger a custom event that the auth context can listen to
          window.dispatchEvent(new CustomEvent('auth:logout'));
        }
        return Promise.reject(error);
      }
    );
  }

  setToken(token: string) {
    localStorage.setItem('jwt_token', token);
    this.client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  }

  clearToken() {
    localStorage.removeItem('jwt_token');
    delete this.client.defaults.headers.common['Authorization'];
  }

  getToken(): string | null {
    return localStorage.getItem('jwt_token');
  }

  // Authentication endpoints
  async login(username: string, password: string) {
    const response = await this.client.post('/auth/login', { loginIdentifier: username, password });
    this.setToken(response.data.token);
    return response.data;
  }

  async register(userData: {
    email: string;
    username: string;
    password: string;
    confirmPassword: string;
  }) {
    const response = await this.client.post('/auth/register', userData);
    // Don't set token on registration - user should login separately
    return response.data;
  }

  async changePassword(currentPassword: string, newPassword: string, confirmNewPassword: string) {
    return this.client.post('/auth/change-password', {
      currentPassword,
      newPassword,
      confirmNewPassword,
    });
  }

  async unregister(password: string) {
    return this.client.delete('/auth/unregister', { password });
  }

  // Admin endpoints (require admin role)
  async adminGetAllUsers() {
    const response = await this.client.get('/manage/users');
    return response.data;
  }

  async adminGetUserById(userId: string) {
    const response = await this.client.get(`/manage/users/${userId}`);
    return response.data;
  }

  async adminUpdateUserRole(username: string, role: string) {
    const response = await this.client.put('/manage/users/role', {
      username,
      role,
    });
    return response.data;
  }

  async adminRemoveUserRole(username: string, role: string) {
    const response = await this.client.delete('/manage/users/role', {
      username,
      role,
    });
    return response.data;
  }

  async adminGetAllQuotes() {
    const response = await this.client.get('/manage/quotes');
    return response.data;
  }

  async adminAddQuote() {
    const response = await this.client.post('/manage/quotes/fetch');
    return response.data;
  }

  async adminUpdateQuote(id: number, quote: { text: string; author: string }) {
    // OVH backend doesn't have update quote endpoint, but keeping for compatibility
    const response = await this.client.put(`/manage/quotes/${id}`, quote);
    return response.data;
  }

  async adminDeleteQuote(id: number) {
    // OVH backend doesn't have delete quote endpoint, but keeping for compatibility
    const response = await this.client.delete(`/manage/quotes/${id}`);
    return response.data;
  }

  async adminGetStats() {
    const response = await this.client.get('/manage/stats');
    return response.data;
  }

  async adminDeleteUser(username: string) {
    const response = await this.client.delete('/manage/users/account', {
      data: { username }
    });
    return response.data;
  }
}

// Create and export a singleton instance
const apiService = new ApiService();

// Set token from localStorage if it exists
const token = apiService.getToken();
if (token) {
  apiService.setToken(token);
}

export default apiService;
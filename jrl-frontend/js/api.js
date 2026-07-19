/**
 * Talks to jrl-auth-service (see js/config.js for the URL). This replaces
 * the old localStorage mock entirely for auth — sign-up/sign-in/profile
 * are now real HTTP calls, real BCrypt hashing, real JWTs.
 *
 * Route/policy management is NOT here. There's no admin API yet — see
 * "coming soon" on the dashboard — so this file only covers identity.
 */
const JRL = (() => {
  const API_BASE = (window.JRL_CONFIG && window.JRL_CONFIG.apiBaseUrl) || 'http://localhost:8081';
  const TOKEN_KEY = 'jrl_token';
  const USER_KEY = 'jrl_user';

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function setSession(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  async function request(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;

    let response;
    try {
      response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    } catch (e) {
      throw new Error(`Could not reach the auth service at ${API_BASE}. Is it running?`);
    }

    let body = null;
    try { body = await response.json(); } catch { /* empty body is fine */ }

    if (!response.ok) {
      throw new Error((body && body.message) || `Request failed (${response.status})`);
    }
    return body;
  }

  async function signUp({ name, email, password }) {
    const data = await request('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ name, email, password }),
    });
    setSession(data.token, data.user);
    return data.user;
  }

  async function signIn({ email, password }) {
    const data = await request('/api/auth/signin', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    setSession(data.token, data.user);
    return data.user;
  }

  function signOut() {
    // Stateless JWTs aren't revoked server-side — this just forgets the
    // token locally. See jrl-auth-service's README for the tradeoff.
    clearSession();
  }

  /**
   * Fast, no-network check: is there a token+profile cached locally at all?
   * Good enough to decide "show the page or bounce to sign-in" without
   * making every page wait on a round trip. Does NOT confirm the token is
   * still valid server-side — call `me()` for that.
   */
  function cachedUser() {
    if (!getToken()) return null;
    try {
      return JSON.parse(localStorage.getItem(USER_KEY));
    } catch {
      return null;
    }
  }

  /**
   * Verifies the token against the server and refreshes the cached profile.
   * Throws if the token is missing, invalid, or expired — callers should
   * treat that as "not signed in" and redirect to sign-in.
   */
  async function me() {
    const user = await request('/api/auth/me', { method: 'GET' });
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    return user;
  }

  async function updateProfile({ name }) {
    const user = await request('/api/auth/me', { method: 'PUT', body: JSON.stringify({ name }) });
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    return user;
  }

  return { signUp, signIn, signOut, cachedUser, me, updateProfile };
})();

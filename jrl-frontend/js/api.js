/**
 * Talks to jrl-auth-service (see js/config.js for the URL). Covers auth
 * (sign-up/sign-in/profile) and the route/policy admin API — everything
 * this frontend needs is a real HTTP call now, nothing here is mocked.
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

  // ---------- routes ----------
  // Every RouteResponse includes `active` (Postgres's setting) and `live`
  // (a fresh Redis read, done server-side on every call) — `live` is what
  // the dashboard's green/red dot reflects.
  function listRoutes() {
    return request('/api/routes', { method: 'GET' });
  }

  function getRoute(routeId) {
    return request(`/api/routes/${routeId}`, { method: 'GET' });
  }

  // Lightweight poll for just the dot, without re-fetching the whole route.
  function getRouteStatus(routeId) {
    return request(`/api/routes/${routeId}/status`, { method: 'GET' });
  }

  function createRoute({ name, targetUrl }) {
    return request('/api/routes', { method: 'POST', body: JSON.stringify({ name, targetUrl }) });
  }

  function updateRoute(routeId, { name, targetUrl, active }) {
    return request(`/api/routes/${routeId}`, { method: 'PUT', body: JSON.stringify({ name, targetUrl, active }) });
  }

  function deleteRoute(routeId) {
    return request(`/api/routes/${routeId}`, { method: 'DELETE' });
  }

  // ---------- policies ----------
  function listPolicies(routeId) {
    return request(`/api/routes/${routeId}/policies`, { method: 'GET' });
  }

  // data: { scope, identifierSource, identifierValue, algorithm, algorithmConfig }
  function createPolicy(routeId, data) {
    return request(`/api/routes/${routeId}/policies`, { method: 'POST', body: JSON.stringify(data) });
  }

  // data: { algorithm, algorithmConfig, active } ONLY — the backend rejects
  // scope/identifierSource/identifierValue on update by design (those are
  // baked into the Redis counter key; changing them means delete + recreate).
  function updatePolicy(routeId, policyId, data) {
    return request(`/api/routes/${routeId}/policies/${policyId}`, { method: 'PUT', body: JSON.stringify(data) });
  }

  function deletePolicy(routeId, policyId) {
    return request(`/api/routes/${routeId}/policies/${policyId}`, { method: 'DELETE' });
  }

  return {
    signUp, signIn, signOut, cachedUser, me, updateProfile,
    listRoutes, getRoute, getRouteStatus, createRoute, updateRoute, deleteRoute,
    listPolicies, createPolicy, updatePolicy, deletePolicy,
  };
})();

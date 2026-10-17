// Production environment. nginx serves the frontend and proxies /api/ to the
// backend container, so the base URL is just the current origin.
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
};

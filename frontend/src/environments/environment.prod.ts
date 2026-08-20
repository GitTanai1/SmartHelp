// Production API URL — set ANGULAR_API_BASE_URL at build time via Vercel
// environment variables, or replace this string directly before deploying.
// Example Vercel env var: ANGULAR_API_BASE_URL=https://smarthelp-backend.onrender.com/api
export const environment = {
  production: true,
  apiBaseUrl: '%%ANGULAR_API_BASE_URL%%',
};

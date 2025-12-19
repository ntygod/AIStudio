/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_APP_NAME: string;
  readonly VITE_APP_VERSION: string;
  readonly VITE_TOKEN_REFRESH_THRESHOLD: string;
  readonly VITE_SSE_TIMEOUT: string;
  readonly VITE_CACHE_TTL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string;
  readonly VITE_POLLING_INTERVAL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

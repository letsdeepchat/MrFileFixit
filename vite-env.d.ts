/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string;
  readonly VITE_APP_DESCRIPTION: string;
  readonly VITE_APP_VERSION: string;
  readonly VITE_ENABLE_ANALYTICS: string;
  readonly VITE_ENABLE_DEBUG: string;
  readonly VITE_ENABLE_SERVICE_WORKER: string;
  readonly VITE_ENABLE_OFFLINE_MODE: string;
  readonly VITE_MAX_FILE_SIZE: string;
  readonly VITE_MAX_FILES_PER_BATCH: string;
  readonly VITE_GEMINI_API_KEY: string;
  readonly VITE_OPENAI_API_KEY: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
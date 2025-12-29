// Environment configuration
export const config = {
  app: {
    title: import.meta.env.VITE_APP_TITLE || 'MrFileFixit',
    description: import.meta.env.VITE_APP_DESCRIPTION || 'All-in-One File Tools',
    version: import.meta.env.VITE_APP_VERSION || '1.0.0',
  },
  features: {
    analytics: import.meta.env.VITE_ENABLE_ANALYTICS === 'true',
    debug: import.meta.env.VITE_ENABLE_DEBUG === 'true',
    serviceWorker: import.meta.env.VITE_ENABLE_SERVICE_WORKER !== 'false',
    offlineMode: import.meta.env.VITE_ENABLE_OFFLINE_MODE !== 'false',
  },
  limits: {
    maxFileSize: import.meta.env.VITE_MAX_FILE_SIZE || '50MB',
    maxFilesPerBatch: parseInt(import.meta.env.VITE_MAX_FILES_PER_BATCH) || 10,
  },
  api: {
    geminiKey: import.meta.env.VITE_GEMINI_API_KEY,
    openaiKey: import.meta.env.VITE_OPENAI_API_KEY,
  },
  // Serverless environment detection
  isServerless: typeof window === 'undefined',
  isDevelopment: import.meta.env.DEV,
  isProduction: import.meta.env.PROD,
};

// Validation
export const validateConfig = () => {
  const errors: string[] = [];
  
  if (!config.app.title) {
    errors.push('VITE_APP_TITLE is required');
  }
  
  if (config.features.debug && config.isProduction) {
    console.warn('Debug mode is enabled in production');
  }
  
  return {
    isValid: errors.length === 0,
    errors,
  };
};
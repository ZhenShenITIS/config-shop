import { defineConfig } from 'orval';

export default defineConfig({
  configShop: {
    input: '../api/openapi.yaml',
    output: {
      target: 'src/api/generated/configShop.ts',
      client: 'react-query',
      httpClient: 'fetch',
      override: {
        fetch: { includeHttpResponseReturnType: false },
        mutator: { path: 'src/api/http.ts', name: 'apiFetch' },
      },
    },
  },
});

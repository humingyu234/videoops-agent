import { getRuntimeAppAdapter } from '../auth/api';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import type { GeneratedScripts, GenerateScriptInput } from './types';

export interface ScriptGenerationApi {
  generate(input: GenerateScriptInput): Promise<GeneratedScripts>;
}

export function createScriptGenerationApi(
  adapter: RuoYiAdapter,
): ScriptGenerationApi {
  return {
    generate(input) {
      return adapter.request<GeneratedScripts>('/api/studio/scripts/generate', {
        data: input,
        method: 'POST',
      });
    },
  };
}

export const scriptGenerationApi: ScriptGenerationApi = {
  generate: (input) =>
    createScriptGenerationApi(getRuntimeAppAdapter()).generate(input),
};

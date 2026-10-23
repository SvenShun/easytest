import { asPage, queryString, request } from './client';
import type { AiDraftResponse, AiJob } from './types';

export const aiApi = {
  generate: (input: { requirementId: string | null; requirementText: string; sampleCount: number }) =>
    request<AiDraftResponse>('core', '/api/v1/ai/bdd-drafts', { method: 'POST', body: input }),
  jobs: (page = 1, size = 10) =>
    request<unknown>('core', `/api/v1/ai/jobs${queryString({ page, size })}`).then((data) =>
      asPage<AiJob>(data, page, size),
    ),
  job: (jobId: string) => request<AiJob>('core', `/api/v1/ai/jobs/${encodeURIComponent(jobId)}`),
};

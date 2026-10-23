import { asPage, queryString, request } from './client';
import type { CaseDetail, CaseInput, CaseReview, ReviewAction } from './types';

export interface CaseListParams {
  reviewStatus?: string;
  keyword?: string;
  requirementId?: string;
  page?: number;
  size?: number;
}

export const casesApi = {
  list: (params: CaseListParams = {}) => {
    const page = params.page ?? 1;
    const size = params.size ?? 20;
    return request<unknown>(
      'core',
      `/api/v1/cases${queryString({
        reviewStatus: params.reviewStatus,
        keyword: params.keyword,
        requirementId: params.requirementId,
        page,
        size,
      })}`,
    ).then((data) => asPage<CaseDetail>(data, page, size));
  },
  get: (id: string) => request<CaseDetail>('core', `/api/v1/cases/${encodeURIComponent(id)}`),
  create: (input: CaseInput) =>
    request<CaseDetail>('core', '/api/v1/cases', { method: 'POST', body: input }),
  update: (id: string, input: CaseInput) =>
    request<CaseDetail>('core', `/api/v1/cases/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: input,
    }),
  remove: (id: string) =>
    request<void>('core', `/api/v1/cases/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  review: (id: string, action: ReviewAction, comment?: string) =>
    request<CaseDetail>('core', `/api/v1/cases/${encodeURIComponent(id)}/review`, {
      method: 'POST',
      body: { action, comment: comment ?? '' },
    }),
  reviews: (id: string) =>
    request<CaseReview[]>('core', `/api/v1/cases/${encodeURIComponent(id)}/reviews`),
};

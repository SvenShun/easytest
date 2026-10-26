import { asPage, queryString, request } from './client';

export interface UiElement {
  id: string;
  name: string;
  elementType: string;
  elementValue: string;
  description?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const uiElementsApi = {
  list: (keyword = '', page = 1, size = 50) =>
    request<unknown>('core', `/api/v1/ui-elements${queryString({ keyword, page, size })}`)
      .then((data) => asPage<UiElement>(data, page, size)),
  create: (input: { name: string; elementType: string; elementValue: string; description?: string }) =>
    request<UiElement>('core', '/api/v1/ui-elements', { method: 'POST', body: input }),
  update: (id: string, input: { name: string; elementType: string; elementValue: string; description?: string }) =>
    request<UiElement>('core', `/api/v1/ui-elements/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  remove: (id: string) => request<void>('core', `/api/v1/ui-elements/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};

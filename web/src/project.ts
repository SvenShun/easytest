export const PROJECT_KEY = 'easytest.projectId';
export const DEFAULT_PROJECT_ID = 'default';

export function getCurrentProjectId(): string {
  try {
    return window.localStorage.getItem(PROJECT_KEY) || DEFAULT_PROJECT_ID;
  } catch {
    return DEFAULT_PROJECT_ID;
  }
}

export function setCurrentProjectId(projectId: string) {
  try {
    window.localStorage.setItem(PROJECT_KEY, projectId || DEFAULT_PROJECT_ID);
  } catch {
    // ignore
  }
}

import { request, requestBlob, saveBlob } from './client';

export type GroupResource = {
  id: number; groupId: number; taskId?: number; type: 'LINK' | 'FILE'; title: string;
  url?: string; originalFilename?: string; contentType?: string; sizeBytes?: number;
  createdByMemberId: number; createdByNickname: string; createdAt: string; canDelete: boolean;
};

export const resourceApi = {
  groupList: (groupId: number) => request<GroupResource[]>(`/groups/${groupId}/resources`, {}, true),
  taskList: (taskId: number) => request<GroupResource[]>(`/tasks/${taskId}/resources`, {}, true),
  addLink: (groupId: number, title: string, url: string, taskId?: number) =>
    request<GroupResource>(`/groups/${groupId}/resources/links`, {
      method: 'POST', body: JSON.stringify({ title, url, taskId }),
    }, true),
  upload: (groupId: number, file: File, title: string, taskId?: number) => {
    const body = new FormData();
    body.append('file', file);
    if (title.trim()) body.append('title', title.trim());
    const query = taskId ? `?taskId=${taskId}` : '';
    return request<GroupResource>(`/groups/${groupId}/resources/files${query}`, { method: 'POST', body }, true);
  },
  remove: (resourceId: number) => request<void>(`/resources/${resourceId}`, { method: 'DELETE' }, true),
  download: async (resource: GroupResource) => {
    const result = await requestBlob(`/resources/${resource.id}/download`, resource.originalFilename ?? 'download');
    saveBlob(result.blob, result.filename);
  },
};

import { request } from './client';

/** 승인이 필요한 쓰기 작업. Python 쪽 interrupt payload 그대로다. */
export type PendingApproval = {
  type: 'approval_request';
  action: string;
  summary: string;
  details: Record<string, unknown>;
};

export type TurnResponse = {
  threadId: string;
  status: 'completed' | 'awaiting_approval';
  reply: string;
  pending?: PendingApproval;
};

export type IndexResponse = {
  indexed: number; skipped: number; removed: number; unsupported: number; failures: string[];
};

export type AgentHealth = { status: 'ok' | 'disabled'; enabled: boolean; missing: string[] };

// LLM 왕복에 도구 호출까지 겹치면 기본 30초를 넘긴다. 리포트 생성과 같은 여유를 준다.
const AGENT_TIMEOUT_MS = 120_000;

export const aiApi = {
  health: () => request<AgentHealth>('/ai/health', {}, true),
  chat: (groupId: number, message: string, threadId?: string) =>
    request<TurnResponse>('/ai/chat', {
      method: 'POST', body: JSON.stringify({ groupId, message, threadId }),
    }, true, AGENT_TIMEOUT_MS),
  resume: (groupId: number, threadId: string, approved: boolean, note = '') =>
    request<TurnResponse>('/ai/resume', {
      method: 'POST', body: JSON.stringify({ groupId, threadId, approved, note }),
    }, true, AGENT_TIMEOUT_MS),
  reindex: (groupId: number) =>
    request<IndexResponse>(`/ai/groups/${groupId}/index`, { method: 'POST' }, true, AGENT_TIMEOUT_MS),
};

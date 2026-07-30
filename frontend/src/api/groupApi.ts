import { request, requestBlob } from './client';

export type GroupResponse = {
  id: number;
  type: 'PERSONAL' | 'TEAM';
  name: string;
  description?: string;
  imageUrl?: string;
  timezone: string;
  dashboardVisibility: 'LEADER_ONLY' | 'MEMBERS';
  membershipPlan: 'FREE' | 'PAID';
  joinCodeActive: boolean;
  joinCode?: string;
  memberId: number;
  role: 'LEADER' | 'MEMBER';
  paidStartedAt?: string;
  paidUntil?: string;
  nextBillingAt?: string;
  testPlanSwitchEnabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateGroupRequest = {
  name: string;
  description?: string;
  timezone?: string;
};

export type UpdateGroupRequest = {
  name?: string;
  description?: string;
  timezone?: string;
  dashboardVisibility?: 'LEADER_ONLY' | 'MEMBERS';
};

export type MemberResponse = {
  id: number;
  userId: number;
  nickname: string;
  profileImageUrl?: string;
  role: 'LEADER' | 'MEMBER';
  status: 'ACTIVE' | 'LEFT' | 'REMOVED';
  joinedAt: string;
};

export type InvitationResponse = {
  id: number;
  groupId: number;
  email: string;
  status: 'PENDING' | 'ACCEPTED' | 'CANCELLED' | 'EXPIRED';
  expiresAt: string;
  acceptedAt?: string;
  createdAt: string;
};

export type InviteLinkResponse = {
  id: number;
  groupId: number;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  url?: string;
  expiresAt: string;
  usedCount: number;
  createdAt: string;
};

export type ReportAccessResponse = {
  allowed: boolean;
  membershipPlan: 'FREE' | 'PAID';
  scope: 'GROUP' | 'MY';
  periodType: 'WEEKLY' | 'MONTHLY' | 'YEARLY';
  remainingThisWeek?: number;
};

export const groupApi = {
  list: () => request<GroupResponse[]>('/groups', {}, true),
  create: (body: CreateGroupRequest) => request<GroupResponse>('/groups', {
    method: 'POST', body: JSON.stringify(body),
  }, true),
  join: (code: string) => request<GroupResponse>('/groups/join', {
    method: 'POST', body: JSON.stringify({ code }),
  }, true),
  authorizeReport: (groupId: number, scope: 'GROUP' | 'MY', periodType: 'WEEKLY' | 'MONTHLY' | 'YEARLY') =>
    request<ReportAccessResponse>(`/groups/${groupId}/reports/access`, {
      method: 'POST', body: JSON.stringify({ scope, periodType }),
    }, true),
  downloadBasicReport: (
    groupId: number,
    input: {
      scope: 'GROUP' | 'MY';
      periodType: 'WEEKLY' | 'MONTHLY' | 'YEARLY';
      from: string;
      to: string;
      language: 'ko' | 'en';
    },
  ) => requestBlob(`/groups/${groupId}/reports/basic.pdf`, `toesa-report-${input.language}.pdf`, {
    method: 'POST',
    body: JSON.stringify(input),
  }),
  get: (groupId: number) => request<GroupResponse>(`/groups/${groupId}`, {}, true),
  update: (groupId: number, body: UpdateGroupRequest) => request<GroupResponse>(`/groups/${groupId}`, {
    method: 'PATCH', body: JSON.stringify(body),
  }, true),
  uploadImage: (groupId: number, file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<GroupResponse>(`/groups/${groupId}/image`, { method: 'POST', body }, true);
  },
  switchTestPlan: (groupId: number, plan: 'FREE' | 'PAID') =>
    request<GroupResponse>(`/groups/${groupId}/membership/test-plan`, {
      method: 'PUT', body: JSON.stringify({ plan }),
    }, true),
  createJoinCode: (groupId: number) => request<GroupResponse>(`/groups/${groupId}/join-code`, {
    method: 'POST',
  }, true),
  rotateJoinCode: (groupId: number) => request<GroupResponse>(`/groups/${groupId}/join-code`, {
    method: 'PUT',
  }, true),
  revokeJoinCode: (groupId: number) => request<void>(`/groups/${groupId}/join-code`, {
    method: 'DELETE',
  }, true),
  members: (groupId: number) => request<MemberResponse[]>(`/groups/${groupId}/members`, {}, true),
  invitations: (groupId: number) => request<InvitationResponse[]>(`/groups/${groupId}/invitations`, {}, true),
  invite: (groupId: number, email: string) => request<InvitationResponse>(`/groups/${groupId}/invitations`, {
    method: 'POST', body: JSON.stringify({ email }),
  }, true),
  createInviteLink: (groupId: number) => request<InviteLinkResponse>(`/groups/${groupId}/invite-links`, {
    method: 'POST',
  }, true),
  inviteLinks: (groupId: number) => request<InviteLinkResponse[]>(`/groups/${groupId}/invite-links`, {}, true),
  revokeInviteLink: (groupId: number, linkId: number) => request<void>(`/groups/${groupId}/invite-links/${linkId}`, {
    method: 'DELETE',
  }, true),
  cancelInvitation: (groupId: number, invitationId: number) => request<void>(`/groups/${groupId}/invitations/${invitationId}`, {
    method: 'DELETE',
  }, true),
  acceptInvitation: (token: string) => request<MemberResponse>(`/group-invitations/${encodeURIComponent(token)}/accept`, {
    method: 'POST',
  }, true),
  changeMemberRole: (groupId: number, memberId: number, role: 'LEADER' | 'MEMBER') =>
    request<MemberResponse>(`/groups/${groupId}/members/${memberId}/role`, {
      method: 'PATCH', body: JSON.stringify({ role }),
    }, true),
  removeMember: (groupId: number, memberId: number) => request<void>(`/groups/${groupId}/members/${memberId}`, {
    method: 'DELETE',
  }, true),
  leave: (groupId: number) => request<void>(`/groups/${groupId}/members/me`, {
    method: 'DELETE',
  }, true),
};

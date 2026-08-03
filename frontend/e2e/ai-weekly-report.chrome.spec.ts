import { expect, test, type Route } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const group = {
  id: 1,
  type: 'TEAM',
  name: '실무 리포트 팀',
  description: 'AI 주간 리포트 검증 팀',
  timezone: 'Asia/Seoul',
  dashboardVisibility: 'MEMBERS',
  membershipPlan: 'PAID',
  joinCodeActive: false,
  memberId: 10,
  role: 'LEADER',
  createdAt: '2026-07-01T09:00:00',
  updatedAt: '2026-07-01T09:00:00',
};

const taskReference = {
  ref: 'TASK-01',
  type: 'TASK',
  label: '결제 API 검토',
  url: '/tasks/101',
  secondaryLabel: '지연 · 높은 우선순위',
};

function memberWork(ref: string, label: string, delayed = 0) {
  return {
    member: { ref, type: 'MEMBER', label },
    assigned: 1,
    active: 1,
    completed: 0,
    delayed,
  };
}

function taskWork(
  ref: string,
  label: string,
  overrides: Record<string, unknown> = {},
) {
  return {
    task: {
      ref,
      type: 'TASK',
      label,
      secondaryLabel: 'TODO',
      url: `/tasks/${ref}`,
    },
    status: 'TODO',
    priority: 'NORMAL',
    dueState: 'NONE',
    checklistTotal: 0,
    checklistCompleted: 0,
    changes: [],
    ...overrides,
  };
}

const draft = {
  headlineTemplate: '이번 주 실행 우선순위를 검토하세요.',
  summary: item('확정된 업무 흐름을 실행 관점에서 점검해야 합니다.', ['tasks.total']),
  changes: [
    item('완료 흐름과 남은 업무를 함께 비교해 봐야 합니다.', ['tasks.completed']),
    item('지난주보다 완료 업무가 늘었습니다.', ['tasks.completed']),
  ],
  achievements: [
    item('완료된 업무가 다음 실행 계획의 근거가 됩니다.', ['tasks.completed']),
    item('완료율 개선 흐름이 확인됐습니다.', ['tasks.completed']),
  ],
  risks: [
    {
      ...item('LOW 위험 후보입니다.', ['tasks.total']),
      severity: 'LOW',
    },
    {
      ...item('첫 번째 HIGH 위험 후보입니다.', ['tasks.delayed']),
      severity: 'HIGH',
    },
    {
      ...item('두 번째 HIGH 위험 후보입니다.', ['tasks.delayed']),
      severity: 'HIGH',
    },
    {
      ...item('MEDIUM 위험 후보입니다.', ['tasks.completed']),
      severity: 'MEDIUM',
    },
  ],
  topActions: [
    {
      priority: 1,
      actionTemplate: 'P1 지연 업무의 다음 조치와 검토 시점을 확정하세요.',
      reasonTemplate: '구체적인 후속 조치가 병목의 다음 주 이월을 막습니다.',
      ownerRef: 'MEMBER-01',
      evidenceKeys: ['tasks.delayed'],
      taskRefs: ['TASK-01'],
      objectiveRefs: [],
    },
    {
      priority: 2,
      actionTemplate: 'P2 완료 업무의 후속 목표를 확인하세요.',
      reasonTemplate: '완료 흐름을 다음 목표와 연결해야 합니다.',
      ownerRef: 'MEMBER-01',
      evidenceKeys: ['tasks.completed'],
      taskRefs: [],
      objectiveRefs: [],
    },
    {
      priority: 3,
      actionTemplate: 'P3 전체 업무량을 다시 점검하세요.',
      reasonTemplate: '다음 주 업무량 판단에 전체 건수가 필요합니다.',
      evidenceKeys: ['tasks.total'],
      taskRefs: [],
      objectiveRefs: [],
    },
  ],
  leaderDecisions: [
    {
      questionTemplate: '지연 업무의 우선순위를 조정할지 결정하시겠습니까?',
      impactTemplate: '이 결정에 따라 다음 실행 순서가 달라집니다.',
      evidenceKeys: ['tasks.delayed'],
      taskRefs: ['TASK-01'],
      objectiveRefs: [],
    },
    {
      questionTemplate: '다음 주 업무량을 조정하시겠습니까?',
      impactTemplate: '팀 전체 실행 여력에 영향을 줍니다.',
      evidenceKeys: ['tasks.total'],
      taskRefs: [],
      objectiveRefs: [],
    },
  ],
  limitations: [
    item('일부 활동은 완전한 추적 범위 밖의 데이터일 수 있습니다.', ['coverage.partial']),
  ],
};

test('기본 리포트와 AI 리포트가 범위·기간 선택을 공유한다', async ({ page, context }) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/dashboard');
  await expect(page.getByRole('combobox', { name: '범위' })).toHaveValue('GROUP');
  await page.getByRole('combobox', { name: '범위' }).selectOption('MY');
  await page.getByRole('combobox', { name: '기간' }).selectOption('MONTHLY');

  await expect(page.getByRole('combobox', { name: '범위' })).toHaveValue('MY');
  await expect(page.getByRole('combobox', { name: '기간' })).toHaveValue('MONTHLY');
  await expect(page.getByLabel('완료된 주간의 월요일')).toHaveCount(0);
  await expect(page.getByRole('tab', { name: 'AI 주간 리포트' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'AI 리포트' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'AI 리포트' }))
    .toHaveAttribute('title', 'AI 리포트는 그룹 전체·주간 기본 리포트를 사용합니다.');
  await expect(page.getByText('AI 리포트는 그룹 전체·주간 기본 리포트를 사용합니다.'))
    .toHaveClass(/sr-only/);

  await page.getByRole('combobox', { name: '범위' }).selectOption('GROUP');
  await page.getByRole('combobox', { name: '기간' }).selectOption('WEEKLY');

  await expect(page.getByRole('combobox', { name: '범위' })).toHaveValue('GROUP');
  await expect(page.getByRole('combobox', { name: '기간' })).toHaveValue('WEEKLY');
  await expect(page.getByRole('button', { name: 'AI 리포트' })).toBeEnabled();
});

test('표준 리포트 reader에서 편집·재생성·확정한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/dashboard');
  await page.waitForLoadState('networkidle');
  await expect(page.getByRole('tab', { name: 'AI 주간 리포트' })).toHaveCount(0);
  await expect(page.getByLabel('완료된 주간의 월요일')).toHaveCount(0);
  await expect(page.getByRole('combobox', { name: '범위' })).toHaveValue('GROUP');
  await expect(page.getByRole('combobox', { name: '기간' })).toHaveValue('WEEKLY');
  await expect(page.locator('.ai-report-document')).toHaveCount(0);

  // 대시보드의 'AI 리포트' 행동은 더 이상 이 페이지를 이동시키지 않고 별도 창을 연다.
  // 이 테스트의 대상은 reader의 편집·재생성·확정이므로 reader를 직접 연다.
  await page.goto('/groups/1/reports/ai-weekly/1');
  await page.waitForLoadState('networkidle');

  await expect(page.getByRole('button', { name: '표준' }))
    .toHaveAttribute('title', '팀장이 과정·결과·진행 상황과 다음 행동을 한눈에 봅니다.');
  await expect(page.getByRole('button', { name: '요약' }))
    .toHaveAttribute('title', '회의에 필요한 핵심 판단·결정 안건·실행 항목만 봅니다.');
  await expect(page.getByRole('button', { name: '상세' }))
    .toHaveAttribute('title', '일별·팀원별·업무별 흐름과 근거까지 검토합니다.');
  await expect(page.getByRole('heading', {
    name: '이번 주 실행 우선순위를 검토하세요.',
  })).toBeVisible();
  await page.getByRole('button', { name: '상세' }).click();
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1?density=DETAILED');
  await expect(page.locator('.ai-report-document')).toContainText('실무 리포트 팀');
  await expect(page.getByText('김팀장', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('이개발', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '결제 API 검토' }).first()).toBeVisible();
  await expect(page.getByText('체크리스트', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'AI 위험 후보' })).toBeVisible();
  await expect(page.getByText('P1 지연 업무의 다음 조치와 검토 시점을 확정하세요.').first())
    .toBeVisible();
  await expect(page.locator('.ai-report-item.action')
    .filter({ hasText: 'P1 지연 업무의 다음 조치와 검토 시점을 확정하세요.' }))
    .toContainText('담당: 김팀장');
  await page.locator('.ai-report-item.action')
    .filter({ hasText: 'P1 지연 업무의 다음 조치와 검토 시점을 확정하세요.' })
    .locator('summary')
    .click();
  await expect(page.getByRole('link', { name: /결제 API 검토/ }).first()).toHaveAttribute(
    'href',
    '/tasks/101',
  );
  await expect(page.locator('body')).not.toContainText('\uFFFD');
  expect(await page.evaluate(() => document.characterSet)).toBe('UTF-8');

  await page.getByRole('button', { name: '초안 편집' }).click();
  await page.getByLabel('제목').fill('편집된 실행 우선순위');
  await expect(page.getByLabel('담당').first()).toHaveValue('MEMBER-01');
  await page.getByLabel('담당').first().selectOption('MEMBER-02');
  await page.getByRole('button', { name: '초안 저장' }).click();
  await expect(page.getByRole('heading', { name: '편집된 실행 우선순위' })).toBeVisible();
  await expect(page.locator('.ai-report-item.action').first()).toContainText('담당: 이개발');
    expect(api.editRequests).toBe(1);

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '새 리비전 생성' }).click();
  await expect.poll(() => api.regenerationRequests).toBe(1);
  await expect(page.getByRole('heading', { name: '재생성된 실행 우선순위' })).toBeVisible();
  await expect(page.getByLabel('리비전')).toHaveValue('2');
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/2?density=DETAILED');

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: '리포트 확정' }).click();
  await expect.poll(() => api.finalizationRequests).toBe(1);
  await expect(page.getByText('확정됨', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '초안 편집' })).toHaveCount(0);
});

test('finalized print 화면은 frozen 위험 순서와 기존 근거 마크업을 유지한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  api.report.publicationStatus = 'FINALIZED';
  api.report.finalizedAt = '2026-07-20T10:00:00';
  api.report.metrics.riskSignals = [
    {
      code: 'HIGH_PRIORITY_PRESENT',
      severity: 'MEDIUM',
      evidenceKeys: ['tasks.total'],
    },
    {
      code: 'OVERDUE_PRESENT',
      severity: 'HIGH',
      evidenceKeys: ['tasks.delayed'],
    },
  ];
  api.report.analysis.risks = [
    {
      text: '첫 번째 frozen AI 위험입니다.',
      severity: 'LOW',
      evidenceKeys: ['tasks.total'],
      taskRefs: [taskReference],
      objectiveRefs: [],
    },
    {
      text: '두 번째 frozen AI 위험입니다.',
      severity: 'HIGH',
      evidenceKeys: ['tasks.delayed'],
      taskRefs: [],
      objectiveRefs: [],
    },
  ];
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1/print');

  await expect(page.getByText('확정됨', { exact: true })).toBeVisible();
  await expect(page.locator('.ai-report-item.server-risk')).toHaveCount(2);
  expect(await page.locator('.ai-report-item.server-risk').evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-risk-code'))))
    .toEqual(['HIGH_PRIORITY_PRESENT', 'OVERDUE_PRESENT']);
  await expect(page.locator('.ai-report-item.risk')).toHaveCount(2);
  expect(await page.locator('.ai-report-item.risk > p').allTextContents())
    .toEqual(['첫 번째 frozen AI 위험입니다.', '두 번째 frozen AI 위험입니다.']);

  const firstAiRisk = page.locator('.ai-report-item.risk').first();
  await expect(firstAiRisk.locator('.ai-report-evidence')).toHaveCount(1);
  await expect(firstAiRisk.locator('.evidence-chip')).toContainText('전체 업무');
  await expect(firstAiRisk.locator('.reference-chip')).toHaveAttribute('href', '/tasks/101');
  await expect(page.locator('.ai-report-linked-tasks')).toHaveCount(0);
  await expect(page.locator('.ai-report-member-exception')).toHaveCount(0);
  await expect(page.locator('.ai-report-contract-warning')).toHaveCount(0);
  expect(api.reportByIdRequests).toBeGreaterThanOrEqual(1);
  expect(api.revisionRequests).toBe(0);
  expect(api.generationRequests).toBe(0);
  expect(api.regenerationRequests).toBe(0);
});

test('첫 리포트의 BASELINE과 서버 위험·AI 위험을 구분하고 밀도를 전환한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  api.report.comparison = { available: false };
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1');
  const metricLabels = page.locator('.ai-report-metrics > div > span');
  const metricValues = page.locator('.ai-report-metrics > div > b');
  const reportSection = (name: string) => page.locator('.ai-report-section').filter({
    has: page.getByRole('heading', { name, exact: true }),
  });
  const serverRisks = reportSection('서버 확인 위험 신호').locator('.server-risk');
  const aiRisks = reportSection('AI 위험 후보').locator('.ai-report-item.risk');
  const achievements = reportSection('이번 주 성과').locator('.ai-report-item');
  const actions = page.locator('.ai-report-item.action');
  const keyChanges = reportSection('이번 주 핵심 변화');
  const decisions = page.locator('.ai-report-item.decision');

  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1');
  await expect(page.getByRole('button', { name: '표준' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(page.getByLabel('리포트 범위')).toHaveValue('GROUP');
  await expect(page.getByText('BASELINE', { exact: true })).toBeVisible();
  await expect(page.getByText('첫 리포트라 지난주 비교 기준이 아직 없습니다.')).toBeVisible();
  await expect(page.getByText('지난주 대비')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '서버 확인 위험 신호' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'AI 위험 후보' })).toBeVisible();
  await expect(page.locator('.ai-report-coverage-warning')).toBeVisible();
  await expect(page.getByRole('heading', { name: '데이터 제한' })).toBeVisible();
  await expect(serverRisks.getByText('지연 업무가 있습니다.', {
    exact: true,
  })).toBeVisible();
  await expect(metricLabels).toHaveText([
    '전체 업무',
    '완료율',
    '기한 준수율',
    '지연',
    '등록 후 완료',
    '보류',
    '체크리스트',
  ]);
  await expect(metricValues).toHaveText(['4', '50%', '67%', '1', '18시간', '0', '3/5']);
  await expect(serverRisks).toHaveCount(2);
  await expect(aiRisks).toHaveCount(4);
  await expect(achievements).toHaveCount(2);
  await expect(actions).toHaveCount(3);
  await expect(decisions).toHaveCount(2);
  await expect(page.locator('.ai-report-executive-brief'))
    .toContainText('확정된 업무 흐름을 실행 관점에서 점검해야 합니다.');
  await expect(keyChanges).toContainText('지난주보다 완료 업무가 늘었습니다.');
  await expect(page.getByRole('heading', { name: '일별 업무 흐름' })).toHaveCount(0);
  await expect(page.locator('.ai-report-task-card')).toHaveCount(0);

  const reportByIdRequestsAtStandard = api.reportByIdRequests;
  const revisionRequestsAtStandard = api.revisionRequests;
  const generationRequestsAtStandard = api.generationRequests;
  const regenerationRequestsAtStandard = api.regenerationRequests;

  await page.getByRole('button', { name: '요약' }).click();
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1?density=SUMMARY');
  await expect(page.getByText('30초 리더 브리프', { exact: true })).toBeVisible();
  await expect(page.locator('.ai-report-coverage-warning')).toBeVisible();
  await expect(page.getByRole('heading', { name: '데이터 제한' })).toBeVisible();
  await expect(metricLabels).toHaveText([
    '전체 업무',
    '완료율',
    '기한 준수율',
    '지연',
  ]);
  await expect(metricValues).toHaveText(['4', '50%', '67%', '1']);
  await expect(serverRisks).toHaveCount(2);
  await expect(aiRisks).toHaveCount(1);
  await expect(aiRisks).toContainText('첫 번째 HIGH 위험 후보입니다.');
  await expect(aiRisks).not.toContainText('두 번째 HIGH 위험 후보입니다.');
  await expect(page.getByRole('heading', { name: '회의 후 실행 항목' })).toBeVisible();
  await expect(actions).toHaveCount(3);
  await expect(actions.first())
    .toContainText('P1 지연 업무의 다음 조치와 검토 시점을 확정하세요.');
  await expect(achievements).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'AI 분석 원문', exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '회의 결정 안건' })).toBeVisible();
  await expect(decisions).toHaveCount(2);
  await expect(page.getByText('지연 업무의 우선순위를 조정할지 결정하시겠습니까?', {
    exact: true,
  })).toBeVisible();
  await expect(page.getByText('다음 주 업무량을 조정하시겠습니까?', {
    exact: true,
  })).toBeVisible();
  await expect(page.getByRole('heading', { name: '일별 업무 흐름' })).toHaveCount(0);
  await expect(page.locator('.ai-report-member-table')).toHaveCount(0);
  await expect(page.locator('.ai-report-task-card')).toHaveCount(0);

  await page.getByRole('button', { name: '상세' }).click();
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1?density=DETAILED');
  await expect(page.locator('.ai-report-coverage-warning')).toBeVisible();
  await expect(page.getByRole('heading', { name: '데이터 제한' })).toBeVisible();
  await expect(metricLabels).toHaveText([
    '전체 업무',
    '완료율',
    '기한 준수율',
    '지연',
    '등록 후 완료',
    '보류',
    '체크리스트',
  ]);
  await expect(metricValues).toHaveText(['4', '50%', '67%', '1', '18시간', '0', '3/5']);
  await expect(serverRisks).toHaveCount(2);
  await expect(aiRisks).toHaveCount(4);
  await expect(achievements).toHaveCount(2);
  await expect(actions).toHaveCount(3);
  await expect(decisions).toHaveCount(2);
  await expect(page.getByRole('heading', { name: '일별 업무 흐름' })).toBeVisible();
  await expect(page.locator('.ai-report-daily-flow')).toBeVisible();
  await expect(page.locator('.ai-report-member-table')).toBeVisible();
  await expect(page.locator('.ai-report-task-card')).toBeVisible();
  expect(await page.locator('.ai-report-evidence').count()).toBeGreaterThan(0);
  await page.waitForLoadState('networkidle');
  expect(api.reportByIdRequests).toBe(reportByIdRequestsAtStandard);
  expect(api.revisionRequests).toBe(revisionRequestsAtStandard);
  expect(api.generationRequests).toBe(generationRequestsAtStandard);
  expect(api.regenerationRequests).toBe(regenerationRequestsAtStandard);
});

test('명시적 taskRefs와 서버 fallback을 frozen 업무에만 연결한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  const member01 = api.report.operations.members[0].member;
  const member02 = api.report.operations.members[1].member;
  api.report.operations.tasks = [
    taskWork('TASK-SHARED', '공유 위험 업무', {
      assignee: member01,
      dueState: 'OVERDUE',
      priority: 'HIGH',
    }),
    taskWork('TASK-OVERDUE-2', '두 번째 지연 업무', {
      assignee: member01,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-HOLD', '미할당 보류 업무', { status: 'ON_HOLD' }),
    taskWork('TASK-AI', 'AI 명시 업무', { assignee: member02 }),
    taskWork('TASK-OUTSIDER', '외부 담당 지연 업무', {
      assignee: { ref: 'MEMBER-99', type: 'MEMBER', label: '현재 멤버 아님' },
      dueState: 'OVERDUE',
    }),
  ];
  api.report.metrics.riskSignals = [
    { code: 'UNKNOWN_B', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
    {
      code: 'HIGH_PRIORITY_PRESENT',
      severity: 'MEDIUM',
      evidenceKeys: ['tasks.highPriority'],
    },
    { code: 'ON_HOLD_PRESENT', severity: 'LOW', evidenceKeys: ['tasks.onHold'] },
    { code: 'OVERDUE_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
    { code: 'UNKNOWN_A', severity: 'LOW', evidenceKeys: ['tasks.onHold'] },
    { code: 'ON_HOLD_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.total'] },
  ];
  const aiTask = api.report.operations.tasks[3].task;
  api.report.analysis.risks = [
    {
      text: '명시적 AI 업무만 연결합니다.',
      severity: 'HIGH',
      evidenceKeys: ['tasks.delayed'],
      taskRefs: [aiTask, aiTask],
      objectiveRefs: [],
    },
    {
      text: 'invalid AI 업무 참조입니다.',
      severity: 'MEDIUM',
      evidenceKeys: ['tasks.delayed'],
      taskRefs: [{
        ref: 'TASK-NOT-FROZEN',
        type: 'TASK',
        label: '존재하지 않는 업무',
        url: '/tasks/not-frozen',
      }],
      objectiveRefs: [],
    },
    {
      text: '업무 참조가 없는 AI 위험입니다.',
      severity: 'LOW',
      evidenceKeys: ['tasks.total'],
      taskRefs: [],
      objectiveRefs: [],
    },
  ];
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1');
  const serverRisks = page.locator('.ai-report-item.server-risk');
  expect(await serverRisks.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-risk-code')))).toEqual([
    'OVERDUE_PRESENT',
    'ON_HOLD_PRESENT',
    'HIGH_PRIORITY_PRESENT',
    'ON_HOLD_PRESENT',
    'UNKNOWN_B',
    'UNKNOWN_A',
  ]);

  const overdueRisk = page.locator(
    '.ai-report-item.server-risk[data-risk-code="OVERDUE_PRESENT"]',
  );
  const overdueRefs = page.locator(
    '.ai-report-item.server-risk[data-risk-code="OVERDUE_PRESENT"] [data-task-ref]',
  );
  await expect(overdueRefs).toHaveCount(3);
  expect(await overdueRefs.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-task-ref')))).toEqual([
    'TASK-SHARED',
    'TASK-OVERDUE-2',
    'TASK-OUTSIDER',
  ]);
  await expect(overdueRisk).toHaveCount(1);
  await expect(page.locator(
    '.ai-report-item.server-risk[data-risk-code="HIGH_PRIORITY_PRESENT"] [data-task-ref]',
  )).toHaveAttribute('data-task-ref', 'TASK-SHARED');
  await expect(page.locator(
    '.ai-report-item.server-risk[data-risk-code="ON_HOLD_PRESENT"]:has(.risk-level.low) [data-task-ref]',
  )).toHaveAttribute('data-task-ref', 'TASK-HOLD');
  const missingEvidenceRisk = page.locator(
    '.ai-report-item.server-risk[data-risk-code="ON_HOLD_PRESENT"]:has(.risk-level.high)',
  );
  await expect(missingEvidenceRisk.locator('[data-task-ref]')).toHaveCount(0);
  await expect(missingEvidenceRisk).toContainText('연결된 frozen 업무 없음');
  const unknownRisks = page.locator(
    '.ai-report-item.server-risk[data-risk-code^="UNKNOWN_"]',
  );
  await expect(unknownRisks.locator('[data-task-ref]')).toHaveCount(0);
  await expect(unknownRisks.first()).toContainText('연결된 frozen 업무 없음');

  const explicitAiRisk = page.locator('.ai-report-item.risk').filter({
    hasText: '명시적 AI 업무만 연결합니다.',
  });
  await expect(explicitAiRisk.locator('[data-task-ref]')).toHaveCount(1);
  await expect(explicitAiRisk.locator('[data-task-ref]'))
    .toHaveAttribute('data-task-ref', 'TASK-AI');
  await expect(explicitAiRisk).not.toContainText('공유 위험 업무');
  const invalidAiRisk = page.locator('.ai-report-item.risk').filter({
    hasText: 'invalid AI 업무 참조입니다.',
  });
  await expect(invalidAiRisk.locator('[data-task-ref]')).toHaveCount(0);
  await expect(invalidAiRisk).toContainText(
    '업무 참조가 frozen 리포트와 일치하지 않아 연결하지 않았습니다.',
  );

  await expect(page.locator('.ai-report-member-exception')).toHaveCount(0);
  await page.getByRole('button', { name: '요약' }).click();
  const memberExceptions = page.locator('.ai-report-member-exception');
  await expect(memberExceptions).toHaveCount(1);
  await expect(memberExceptions).toHaveAttribute('data-member-ref', 'MEMBER-01');
  await expect(memberExceptions).toContainText('연결 업무 2');
  await expect(memberExceptions).toContainText('지연 업무가 있습니다.');
  await expect(memberExceptions).not.toContainText('이개발');
  await page.getByLabel('리포트 범위').selectOption('MEMBER_COMPARISON');
  await expect(memberExceptions).toHaveCount(1);
  await page.getByLabel('리포트 범위').selectOption('INDIVIDUAL_MEMBER');
  await expect(page.getByLabel('팀원 선택')).toHaveValue('MEMBER-01');
  await expect(memberExceptions).toHaveCount(0);
});

test('팀원 예외를 위험·업무·지연·ref 순서로 최대 3명 선택하고 빈 상태를 표시한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  const members = [
    memberWork('MEMBER-A', 'A팀원'),
    memberWork('MEMBER-B', 'B팀원'),
    memberWork('MEMBER-C', 'C팀원'),
    memberWork('MEMBER-D', 'D팀원'),
  ];
  api.report.operations.members = members;
  api.report.operations.tasks = [
    taskWork('TASK-A', 'A 지연', {
      assignee: members[0].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-B', 'B 보류', {
      assignee: members[1].member,
      status: 'ON_HOLD',
    }),
    taskWork('TASK-C-1', 'C 우선 1', {
      assignee: members[2].member,
      priority: 'HIGH',
    }),
    taskWork('TASK-C-2', 'C 우선 2', {
      assignee: members[2].member,
      priority: 'URGENT',
    }),
    taskWork('TASK-D', 'D 우선', {
      assignee: members[3].member,
      priority: 'HIGH',
    }),
  ];
  api.report.metrics.riskSignals = [
    { code: 'HIGH_PRIORITY_PRESENT', severity: 'MEDIUM', evidenceKeys: ['tasks.highPriority'] },
    { code: 'ON_HOLD_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.onHold'] },
    { code: 'OVERDUE_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
  ];

  await page.goto('/groups/1/reports/ai-weekly/1?density=SUMMARY');
  const exceptions = page.locator('.ai-report-member-exception');
  await expect(exceptions).toHaveCount(3);
  expect(await exceptions.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-member-ref')))).toEqual([
    'MEMBER-A',
    'MEMBER-B',
    'MEMBER-C',
  ]);

  const tiedMembers = [
    memberWork('MEMBER-B', 'B팀원', 0),
    memberWork('MEMBER-C', 'C팀원', 3),
    memberWork('MEMBER-D', 'D팀원', 3),
    memberWork('MEMBER-E', 'E팀원', 1),
  ];
  api.report.operations.members = tiedMembers;
  api.report.operations.tasks = [
    taskWork('TASK-B-1', 'B 지연 1', {
      assignee: tiedMembers[0].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-B-2', 'B 지연 2', {
      assignee: tiedMembers[0].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-C', 'C 지연', {
      assignee: tiedMembers[1].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-D', 'D 지연', {
      assignee: tiedMembers[2].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-E', 'E 지연', {
      assignee: tiedMembers[3].member,
      dueState: 'OVERDUE',
    }),
  ];
  api.report.metrics.riskSignals = [
    { code: 'OVERDUE_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
  ];
  await page.reload();
  await expect(exceptions).toHaveCount(3);
  expect(await exceptions.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-member-ref')))).toEqual([
    'MEMBER-B',
    'MEMBER-C',
    'MEMBER-D',
  ]);

  api.report.operations.members = [memberWork('MEMBER-A', 'A팀원')];
  api.report.operations.tasks = [
    taskWork('TASK-UNASSIGNED', '미할당 지연', { dueState: 'OVERDUE' }),
    taskWork('TASK-OUTSIDER', '외부 담당 지연', {
      assignee: { ref: 'MEMBER-X', type: 'MEMBER', label: '외부 담당' },
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-AI-ONLY', 'AI 전용 업무', {
      assignee: api.report.operations.members[0].member,
    }),
  ];
  api.report.analysis.risks = [{
    text: 'AI 위험만 있는 팀원입니다.',
    severity: 'HIGH',
    evidenceKeys: ['tasks.total'],
    taskRefs: [api.report.operations.tasks[2].task],
    objectiveRefs: [],
  }];
  await page.reload();
  await expect(exceptions).toHaveCount(0);
  await expect(page.getByText('확인된 팀원 예외 없음', { exact: true })).toBeVisible();
});

test('MEMBER_COMPARISON은 frozen 순서와 서버 위험 업무만 투영한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  const members = [
    {
      ...memberWork('MEMBER-B', 'B팀원', 2),
      assigned: 4, active: 2, completed: 2, onTimeRatePercent: 50,
    },
    {
      ...memberWork('MEMBER-A', 'A팀원', 1),
      assigned: 3, active: 1, completed: 2, onTimeRatePercent: 75,
    },
    {
      ...memberWork('MEMBER-Z', 'Z팀원'),
      assigned: 1, active: 1, completed: 0,
    },
  ];
  api.report.operations.members = members;
  api.report.operations.tasks = [
    taskWork('TASK-B-HOLD', 'B 보류 업무', {
      assignee: members[0].member,
      status: 'ON_HOLD',
    }),
    taskWork('TASK-UNASSIGNED', '미할당 지연 업무', { dueState: 'OVERDUE' }),
    taskWork('TASK-A-LATE', 'A 지연 업무', {
      assignee: members[1].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-AI-ONLY', 'AI 위험 전용 업무', {
      assignee: members[0].member,
    }),
    taskWork('TASK-B-LATE', 'B 지연 업무', {
      assignee: members[0].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-Z-NORMAL', 'Z 일반 업무', {
      assignee: members[2].member,
    }),
  ];
  api.report.metrics.riskSignals = [
    { code: 'OVERDUE_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
    { code: 'ON_HOLD_PRESENT', severity: 'MEDIUM', evidenceKeys: ['tasks.onHold'] },
  ];
  api.report.analysis.risks = [{
    text: 'AI 위험 전용 업무 후보입니다.',
    severity: 'HIGH',
    evidenceKeys: ['tasks.total'],
    taskRefs: [api.report.operations.tasks[3].task],
    objectiveRefs: [],
  }];
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1');
  const teamSnapshot = async () => ({
    headline: await page.locator('.ai-report-document-header h1').innerText(),
    summary: await page.locator('.ai-report-document-header p').innerText(),
    comparison: await page.locator('.ai-report-comparison, .ai-report-baseline').innerText(),
    kpis: await page.locator('.ai-report-metrics > div').allTextContents(),
    serverRisks: await page.locator('.ai-report-item.server-risk').allTextContents(),
    aiRisks: await page.locator('.ai-report-item.risk').allTextContents(),
    actions: await page.locator('.ai-report-item.action').allTextContents(),
    decisions: await page.locator('.ai-report-item.decision').allTextContents(),
  });
  const groupTeamSnapshot = await teamSnapshot();
  const reportRequests = api.reportByIdRequests;
  const revisionRequests = api.revisionRequests;
  const generationRequests = api.generationRequests;
  const regenerationRequests = api.regenerationRequests;

  await page.getByLabel('리포트 범위').selectOption('MEMBER_COMPARISON');
  await expect(page.getByRole('heading', { name: '팀원 비교' })).toBeVisible();
  expect(await teamSnapshot()).toEqual(groupTeamSnapshot);
  const rows = page.locator('.ai-report-member-comparison-entry');
  expect(await rows.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-member-ref')))).toEqual([
    'MEMBER-B',
    'MEMBER-A',
    'MEMBER-Z',
  ]);
  await expect(rows.nth(0).locator('.report-member-row > *'))
    .toHaveText(['B팀원', '4', '2', '2', '2', '50%']);
  await expect(rows.nth(1).locator('.report-member-row > *'))
    .toHaveText(['A팀원', '3', '1', '2', '1', '75%']);
  await expect(rows.nth(2).locator('.report-member-row > *'))
    .toHaveText(['Z팀원', '1', '1', '0', '0', '-']);
  await expect(page.locator('.ai-report-member-exception')).toHaveCount(0);

  await page.getByRole('button', { name: '상세' }).click();
  const memberBTasks = rows.nth(0).locator('.ai-report-member-risk-task');
  expect(await memberBTasks.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-task-ref')))).toEqual([
    'TASK-B-HOLD',
    'TASK-B-LATE',
  ]);
  await expect(rows.nth(1).locator('.ai-report-member-risk-task'))
    .toHaveAttribute('data-task-ref', 'TASK-A-LATE');
  await expect(rows.nth(2)).toContainText('연결된 서버 위험 업무 없음');
  await expect(page.locator(
    '.ai-report-member-comparison [data-task-ref="TASK-UNASSIGNED"]',
  )).toHaveCount(0);
  await expect(page.locator('[data-task-ref="TASK-AI-ONLY"]')).toHaveCount(1);
  await expect(page.locator(
    '.ai-report-member-comparison [data-task-ref="TASK-AI-ONLY"]',
  )).toHaveCount(0);

  await page.getByRole('button', { name: '요약' }).click();
  await expect(page.locator('.ai-report-member-comparison')).toHaveCount(0);
  await expect(page.locator('.ai-report-member-exception')).toHaveCount(2);
  await page.waitForLoadState('networkidle');
  expect(api.reportByIdRequests).toBe(reportRequests);
  expect(api.revisionRequests).toBe(revisionRequests);
  expect(api.generationRequests).toBe(generationRequests);
  expect(api.regenerationRequests).toBe(regenerationRequests);
});

test('INDIVIDUAL_MEMBER는 frozen KPI와 선택 팀원 업무만 투영한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  const members = [
    {
      ...memberWork('MEMBER-A', 'A팀원', 1),
      assigned: 4, active: 2, completed: 1, onTimeRatePercent: 80,
    },
    {
      ...memberWork('MEMBER-ZERO', '업무 없는 팀원'),
      assigned: 0, active: 0, completed: 0,
    },
    {
      ...memberWork('MEMBER-B', 'B팀원', 1),
      assigned: 1, active: 1, completed: 0, onTimeRatePercent: 25,
    },
  ];
  api.report.operations.members = members;
  api.report.operations.tasks = [
    taskWork('TASK-A-NORMAL', 'A 일반 업무', {
      assignee: members[0].member,
    }),
    taskWork('TASK-UNASSIGNED', '미할당 지연 업무', { dueState: 'OVERDUE' }),
    taskWork('TASK-A-HOLD', 'A 보류 업무', {
      assignee: members[0].member,
      status: 'ON_HOLD',
      checklistTotal: 3,
      checklistCompleted: 1,
      blockerType: 'EXTERNAL_DEPENDENCY',
      changes: ['CHECKLIST_PROGRESS'],
    }),
    taskWork('TASK-A-AI', 'A AI 위험 전용 업무', {
      assignee: members[0].member,
    }),
    taskWork('TASK-B-LATE', 'B 지연 업무', {
      assignee: members[2].member,
      dueState: 'OVERDUE',
    }),
    taskWork('TASK-A-LATE', 'A 지연 업무', {
      assignee: members[0].member,
      dueState: 'OVERDUE',
    }),
  ];
  api.report.metrics.riskSignals = [
    { code: 'ON_HOLD_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.onHold'] },
    { code: 'OVERDUE_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
  ];
  api.report.analysis.risks = [{
    text: 'A 팀원의 AI 위험 전용 업무입니다.',
    severity: 'HIGH',
    evidenceKeys: ['tasks.total'],
    taskRefs: [api.report.operations.tasks[3].task],
    objectiveRefs: [],
  }];
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1?density=SUMMARY');
  const teamSnapshot = async () => ({
    headline: await page.locator('.ai-report-document-header h1').innerText(),
    summary: await page.locator('.ai-report-document-header p').innerText(),
    comparison: await page.locator('.ai-report-comparison, .ai-report-baseline').innerText(),
    kpis: await page.locator('.ai-report-metrics > div').allTextContents(),
    serverRisks: await page.locator('.ai-report-item.server-risk').allTextContents(),
    aiRisks: await page.locator('.ai-report-item.risk').allTextContents(),
    actions: await page.locator('.ai-report-item.action').allTextContents(),
  });
  const groupTeamSnapshot = await teamSnapshot();
  const reportRequests = api.reportByIdRequests;
  const revisionRequests = api.revisionRequests;
  const generationRequests = api.generationRequests;
  const regenerationRequests = api.regenerationRequests;

  await page.getByLabel('리포트 범위').selectOption('INDIVIDUAL_MEMBER');
  await expect(page.getByLabel('팀원 선택')).toHaveValue('MEMBER-A');
  expect(await teamSnapshot()).toEqual(groupTeamSnapshot);
  const individual = page.locator(
    '.ai-report-individual-member[data-member-ref="MEMBER-A"]',
  );
  await expect(individual).toBeVisible();
  await expect(individual.locator('.ai-report-individual-kpis > div'))
    .toHaveText(['담당4', '진행2', '완료1', '지연1', '기한 준수율80%']);
  await expect(individual.locator('.ai-report-individual-risk'))
    .toHaveAttribute('data-risk-code', 'OVERDUE_PRESENT');
  await expect(individual.locator('.ai-report-individual-risk')).toContainText('HIGH');
  await expect(individual.locator('.ai-report-individual-risk'))
    .toContainText('지연 업무가 있습니다.');
  await expect(individual.locator('.ai-report-member-risk-task')).toHaveCount(0);
  await expect(individual.locator('.ai-report-task-card')).toHaveCount(0);

  await page.getByRole('button', { name: '표준' }).click();
  expect(await individual.locator('.ai-report-member-risk-task').evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-task-ref')))).toEqual([
    'TASK-A-HOLD',
    'TASK-A-LATE',
  ]);
  await expect(individual.locator('[data-task-ref="TASK-A-AI"]')).toHaveCount(0);
  await expect(individual.locator('[data-task-ref="TASK-UNASSIGNED"]')).toHaveCount(0);
  await expect(individual.locator('[data-task-ref="TASK-B-LATE"]')).toHaveCount(0);
  await expect(individual.locator('.ai-report-individual-risk')).toHaveCount(0);

  await page.getByRole('button', { name: '상세' }).click();
  const assignedTasks = individual.locator('.ai-report-individual-tasks .ai-report-task-card');
  expect(await assignedTasks.evaluateAll((items) =>
    items.map((item) => item.getAttribute('data-task-ref')))).toEqual([
    'TASK-A-NORMAL',
    'TASK-A-HOLD',
    'TASK-A-AI',
    'TASK-A-LATE',
  ]);
  await expect(individual.locator('[data-task-ref="TASK-A-HOLD"]'))
    .toContainText('체크리스트: 1/3');
  await expect(individual.locator('[data-task-ref="TASK-A-HOLD"]'))
    .toContainText('차단: EXTERNAL_DEPENDENCY');
  await expect(individual.locator('[data-task-ref="TASK-UNASSIGNED"]')).toHaveCount(0);
  await expect(individual.locator('[data-task-ref="TASK-B-LATE"]')).toHaveCount(0);

  await page.getByRole('button', { name: '요약' }).click();
  await page.getByLabel('팀원 선택').selectOption('MEMBER-ZERO');
  const emptyMember = page.locator(
    '.ai-report-individual-member[data-member-ref="MEMBER-ZERO"]',
  );
  await expect(emptyMember.locator('.ai-report-individual-kpis > div'))
    .toHaveText(['담당0', '진행0', '완료0', '지연0', '기한 준수율-']);
  await expect(emptyMember).toContainText('선택된 기간에 담당 업무 없음');
  await expect(page.getByLabel('리포트 범위')).toHaveValue('INDIVIDUAL_MEMBER');
  expect(new URL(page.url()).searchParams.get('memberRef')).toBe('MEMBER-ZERO');
  await page.waitForLoadState('networkidle');
  expect(api.reportByIdRequests).toBe(reportRequests);
  expect(api.revisionRequests).toBe(revisionRequests);
  expect(api.generationRequests).toBe(generationRequests);
  expect(api.regenerationRequests).toBe(regenerationRequests);
});

test('invalid projection query를 기본값으로 복구하고 comparison memberRef를 제거한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1?scope=INVALID');
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1');
  await expect(page.getByLabel('리포트 범위')).toHaveValue('GROUP');

  await page.goto('/groups/1/reports/ai-weekly/1?density=INVALID');
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1');
  await expect(page.getByRole('button', { name: '표준' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );

  await page.goto(
    '/groups/1/reports/ai-weekly/1?scope=INDIVIDUAL_MEMBER&memberRef=MEMBER-99&density=DETAILED',
  );
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1?density=DETAILED');
  await expect(page.getByLabel('리포트 범위')).toHaveValue('GROUP');
  await expect(page.getByLabel('팀원 선택')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '상세' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );

  await page.goto(
    '/groups/1/reports/ai-weekly/1?scope=MEMBER_COMPARISON&memberRef=MEMBER-01',
  );
  await expect(page).toHaveURL(
    '/groups/1/reports/ai-weekly/1?scope=MEMBER_COMPARISON',
  );
  await expect(page.getByLabel('리포트 범위')).toHaveValue('MEMBER_COMPARISON');
  await expect(page.getByLabel('팀원 선택')).toHaveCount(0);
});

test('frozen member 선택을 유지하고 projection 전환 중 report API를 다시 호출하지 않는다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1');
  await expect(page.getByLabel('리포트 범위')).toHaveValue('GROUP');
  const teamSnapshot = async () => ({
    headline: await page.locator('.ai-report-document-header h1').innerText(),
    summary: await page.locator('.ai-report-document-header p').innerText(),
    comparison: await page.locator('.ai-report-comparison, .ai-report-baseline').innerText(),
    kpis: await page.locator('.ai-report-metrics > div').allTextContents(),
    serverRisks: await page.locator('.ai-report-item.server-risk').allTextContents(),
    aiRisks: await page.locator('.ai-report-item.risk').allTextContents(),
    actions: await page.locator('.ai-report-item.action').allTextContents(),
    decisions: await page.locator('.ai-report-item.decision').allTextContents(),
    memberExceptions: await page.locator('.ai-report-member-exception').allTextContents(),
  });
  const groupTeamSnapshot = await teamSnapshot();

  const reportByIdRequestsBeforeProjection = api.reportByIdRequests;
  const revisionRequestsBeforeProjection = api.revisionRequests;
  const generationRequestsBeforeProjection = api.generationRequests;
  const regenerationRequestsBeforeProjection = api.regenerationRequests;

  await page.getByLabel('리포트 범위').selectOption('MEMBER_COMPARISON');
  await expect(page.getByLabel('리포트 범위')).toHaveValue('MEMBER_COMPARISON');
  await expect(page.getByLabel('팀원 선택')).toHaveCount(0);
  expect(await teamSnapshot()).toEqual(groupTeamSnapshot);

  await page.getByLabel('리포트 범위').selectOption('INDIVIDUAL_MEMBER');
  await expect(page.getByLabel('팀원 선택')).toHaveValue('MEMBER-01');
  await expect(page.getByRole('option', { name: '김팀장' })).toHaveAttribute(
    'value',
    'MEMBER-01',
  );
  await expect(page.getByRole('option', { name: '이개발' })).toHaveAttribute(
    'value',
    'MEMBER-02',
  );
  expect(await teamSnapshot()).toEqual(groupTeamSnapshot);

  await page.getByLabel('팀원 선택').selectOption('MEMBER-02');
  await expect(page.getByLabel('팀원 선택')).toHaveValue('MEMBER-02');
  await expect.poll(() => new URL(page.url()).searchParams.get('memberRef'))
    .toBe('MEMBER-02');
  await expect.poll(() => new URL(page.url()).searchParams.get('scope'))
    .toBe('INDIVIDUAL_MEMBER');
  expect(await teamSnapshot()).toEqual(groupTeamSnapshot);
  await page.getByRole('button', { name: '요약' }).click();
  await page.getByRole('button', { name: '상세' }).click();
  await expect.poll(() => new URL(page.url()).searchParams.get('density'))
    .toBe('DETAILED');
  await expect(page.locator('.ai-report-task-card')).toHaveCount(0);
  await expect(page.getByText('선택된 기간에 담당 업무 없음', { exact: true }))
    .toBeVisible();
  await page.waitForLoadState('networkidle');

  expect(api.reportByIdRequests).toBe(reportByIdRequestsBeforeProjection);
  expect(api.revisionRequests).toBe(revisionRequestsBeforeProjection);
  expect(api.generationRequests).toBe(generationRequestsBeforeProjection);
  expect(api.regenerationRequests).toBe(regenerationRequestsBeforeProjection);
});

test('기본 리포트도 팝업 없이 PDF 파일로 다운로드한다', async ({ page, context }) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/dashboard');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'PDF 리포트 생성' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('basic-report-1.pdf');
  const downloadedPath = await download.path();
  expect(downloadedPath).not.toBeNull();
  const downloadedBytes = await readFile(downloadedPath!);
  expect(downloadedBytes.subarray(0, 5).toString('ascii')).toBe('%PDF-');
});

test('stale GENERATING 리포트를 Chrome에서 다시 획득한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  api.report = {
    ...api.report,
    status: 'GENERATING',
    analysis: null,
    draft: null,
  };
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/dashboard');
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: 'AI 리포트' }).click();

  await expect.poll(() => api.generationRequests).toBe(1);
  await expect(page).toHaveURL('/groups/1/reports/ai-weekly/1');
  await expect(page.getByRole('heading', {
    name: '이번 주 실행 우선순위를 검토하세요.',
  })).toBeVisible();
});

test('호환되지 않는 캐시 응답은 대시보드를 백지화하지 않고 오류 상태를 표시한다', async ({
  page,
  context,
}) => {
  const api = new ReportApiFixture();
  delete api.report.operations;
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/dashboard');
  await expect(page.getByRole('heading', { name: '업무 리포트' })).toBeVisible();
  await expect(page.getByText(
    '저장된 AI 리포트 형식이 현재 화면과 호환되지 않습니다. 다시 생성하거나 관리자에게 문의하세요.',
  )).toBeVisible();
  await expect(page.locator('body')).not.toHaveText('');
  expect(pageErrors).toEqual([]);
});

test('지난주 대비 변화를 부호가 아니라 방향으로 읽는다', async ({ page, context }) => {
  const api = new ReportApiFixture();
  api.report.comparison = {
    available: true,
    totalTasksDelta: 0,
    completedTasksDelta: -2,
    delayedTasksDelta: 2,
    onHoldTasksDelta: 0,
    completionRateDeltaPercent: 0,
    checklistCompletionRateDeltaPercent: 0,
  };
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1');

  const deltas = page.locator('.ai-report-delta');
  await expect(deltas).toHaveCount(3);
  // 완료 감소와 지연 증가는 부호가 반대지만 둘 다 악화다.
  await expect(deltas.nth(0)).toHaveClass(/bad/);
  await expect(deltas.nth(0)).toContainText('-2');
  await expect(deltas.nth(1)).toHaveClass(/bad/);
  await expect(deltas.nth(1)).toContainText('+2');
  await expect(deltas.nth(2)).toHaveClass(/flat/);
  await expect(deltas.nth(2)).toContainText('변화 없음');
});

test('같은 안내 문장을 위험 항목마다 반복하지 않는다', async ({ page, context }) => {
  const api = new ReportApiFixture();
  await page.addInitScript(() => {
    localStorage.setItem('accessToken', 'playwright-token');
    localStorage.setItem('language', 'ko');
  });
  await context.route('**/api/v1/**', (route) => api.handle(route));

  await page.goto('/groups/1/reports/ai-weekly/1');
  const serverRisks = page.locator('.ai-report-item.server-risk');
  await expect(serverRisks).toHaveCount(2);
  // 안내는 섹션에 한 번만 있고 항목에는 없다.
  await expect(page.getByText('저장된 업무 수치와 상태 규칙으로 확인한 사실입니다.'))
    .toHaveCount(1);
  await expect(serverRisks.first())
    .not.toContainText('저장된 업무 수치와 상태 규칙으로 확인한 사실입니다.');
});

function item(textTemplate: string, evidenceKeys: string[]) {
  return { textTemplate, evidenceKeys, taskRefs: [], objectiveRefs: [] };
}

class ReportApiFixture {
  report: any = reportResponse();
  reportByIdRequests = 0;
  revisionRequests = 0;
  generationRequests = 0;
  editRequests = 0;
  regenerationRequests = 0;
  finalizationRequests = 0;

  async handle(route: Route) {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = request.method();

    if (path === '/groups' && method === 'GET') return json(route, [group]);
    if (path === '/groups/1' && method === 'GET') return json(route, group);
    if (path === '/notifications' && method === 'GET') {
      return json(route, {
        items: [], page: 1, size: 20, totalElements: 0, totalPages: 0, unreadCount: 0,
      });
    }
    if (path.startsWith('/groups/1/dashboard') && method === 'GET') {
      return json(route, dashboardResponse());
    }
    if (path === '/groups/1/reports/basic.pdf' && method === 'POST') {
      return route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        headers: {
          'Content-Disposition': 'attachment; filename="basic-report-1.pdf"',
        },
        body: '%PDF-1.4\n% deterministic basic report fixture\n%%EOF',
      });
    }
    if (path.startsWith('/groups/1/reports/ai-weekly/revisions') && method === 'GET') {
      this.revisionRequests++;
      return json(route, [{
        reportId: this.report.reportId,
        revision: this.report.revision,
        status: this.report.status,
        publicationStatus: this.report.publicationStatus,
        generatedAt: this.report.generatedAt,
        finalizedAt: this.report.finalizedAt,
      }]);
    }
    if (/\/groups\/1\/reports\/ai-weekly\/\d+$/.test(path) && method === 'GET') {
      this.reportByIdRequests++;
      return json(route, this.report);
    }
    if (path === '/groups/1/reports/ai-weekly' && method === 'GET') {
      return json(route, this.report);
    }
    if (path === '/groups/1/reports/ai-weekly' && method === 'POST') {
      this.generationRequests++;
      this.report = reportResponse();
      return json(route, this.report);
    }
    if (/\/draft$/.test(path) && method === 'PATCH') {
      this.editRequests++;
      const body = request.postDataJSON();
      this.report = withDraft(this.report, body.content, {
        editorVersion: this.report.editorVersion + 1,
      });
      return json(route, this.report);
    }
    if (/\/regenerations$/.test(path) && method === 'POST') {
      this.regenerationRequests++;
      const regenerated = {
        ...draft,
        headlineTemplate: '재생성된 실행 우선순위',
      };
      this.report = withDraft(this.report, regenerated, {
        reportId: 2,
        revision: 2,
        editorVersion: 0,
        publicationStatus: 'DRAFT',
      });
      return json(route, this.report);
    }
    if (/\/finalization$/.test(path) && method === 'POST') {
      this.finalizationRequests++;
      this.report = {
        ...this.report,
        publicationStatus: 'FINALIZED',
        finalizedAt: '2026-07-28T14:00:00',
      };
      return json(route, this.report);
    }
    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'E2E_ROUTE_NOT_FOUND', message: `${method} ${path}` }),
    });
  }
}

function reportResponse() {
  return withDraft({
    reportId: 1,
    status: 'COMPLETED',
    publicationStatus: 'DRAFT',
    periodStart: '2026-07-13',
    periodEnd: '2026-07-19',
    language: 'ko',
    generatedAt: '2026-07-20T09:00:00',
    revision: 1,
    editorVersion: 0,
    cached: false,
    metrics: {
      totalTasks: 4,
      completionRatePercent: 50,
      onTimeRatePercent: 67,
      averageCompletionHours: 18,
      statuses: {
        requested: 0, todo: 1, inProgress: 1, onHold: 0,
        completed: 2, rejected: 0, cancelled: 0, delayed: 1,
      },
      historyCoverage: {
        status: 'PARTIAL',
        trackingStartedAt: '2026-07-15T00:00:00Z',
      },
      checklist: { total: 5, completed: 3, completionRatePercent: 60 },
      daily: [
        { date: '2026-07-13', created: 1, completed: 0 },
        { date: '2026-07-14', created: 1, completed: 1 },
        { date: '2026-07-15', created: 0, completed: 0 },
        { date: '2026-07-16', created: 1, completed: 1 },
        { date: '2026-07-17', created: 1, completed: 0 },
        { date: '2026-07-18', created: 0, completed: 0 },
        { date: '2026-07-19', created: 0, completed: 0 },
      ],
      members: [],
      riskSignals: [
        { code: 'OVERDUE_PRESENT', severity: 'HIGH', evidenceKeys: ['tasks.delayed'] },
        { code: 'HIGH_PRIORITY_PRESENT', severity: 'MEDIUM', evidenceKeys: ['tasks.total'] },
      ],
      evidence: { 'tasks.total': 4, 'tasks.completed': 2, 'tasks.delayed': 1 },
    },
    comparison: {
      available: true,
      totalTasksDelta: 1,
      completedTasksDelta: 1,
      delayedTasksDelta: -1,
      onHoldTasksDelta: 0,
      completionRateDeltaPercent: 10,
      checklistCompletionRateDeltaPercent: 20,
    },
    evidence: {
      'tasks.total': { key: 'tasks.total', label: '전체 업무', value: '4건', kind: 'COUNT' },
      'tasks.completed': {
        key: 'tasks.completed', label: '완료 업무', value: '2건', kind: 'COUNT',
      },
      'tasks.delayed': {
        key: 'tasks.delayed', label: '지연 업무', value: '1건', kind: 'COUNT',
      },
      'coverage.partial': {
        key: 'coverage.partial', label: '이력 범위', value: '부분 수집', kind: 'TEXT',
      },
    },
    operations: {
      groupName: group.name,
      healthStatus: 'AT_RISK',
      confidenceLevel: 'LOW',
      memberCount: 2,
      activeMemberCount: 1,
      members: [
        {
          member: { ref: 'MEMBER-01', type: 'MEMBER', label: '김팀장' },
          assigned: 4, active: 2, completed: 2, delayed: 1, onTimeRatePercent: 67,
        },
        {
          member: { ref: 'MEMBER-02', type: 'MEMBER', label: '이개발' },
          assigned: 0, active: 0, completed: 0, delayed: 0,
        },
      ],
      tasks: [
        {
          task: taskReference,
          assignee: { ref: 'MEMBER-01', type: 'MEMBER', label: '김팀장' },
          status: 'IN_PROGRESS',
          priority: 'HIGH',
          dueState: 'OVERDUE',
          checklistTotal: 3,
          checklistCompleted: 1,
          blockerType: 'EXTERNAL_DEPENDENCY',
          blockerNextActionType: 'FOLLOW_UP',
          blockerReviewWindow: 'DUE_OR_OVERDUE',
          changes: ['DETAILS_CHANGED', 'CHECKLIST_PROGRESS'],
        },
      ],
    },
  }, draft);
}

function withDraft(report: any, content: typeof draft, overrides: Record<string, unknown> = {}) {
  const references = (refs: string[]) => refs.includes('TASK-01') ? [taskReference] : [];
  const owner = (ref?: string) => report.operations.members
    .find((value: any) => value.member.ref === ref)?.member;
  const viewItem = (value: any) => ({
    text: value.textTemplate,
    evidenceKeys: value.evidenceKeys,
    taskRefs: references(value.taskRefs),
    objectiveRefs: [],
  });
  return {
    ...report,
    ...overrides,
    draft: content,
    analysis: {
      headline: content.headlineTemplate,
      summary: viewItem(content.summary),
      changes: content.changes.map(viewItem),
      achievements: content.achievements.map(viewItem),
      risks: content.risks.map((value: any) => ({ ...viewItem(value), severity: value.severity })),
      topActions: content.topActions.map((value: any) => ({
      priority: value.priority,
      action: value.actionTemplate,
      reason: value.reasonTemplate,
      owner: owner(value.ownerRef),
      evidenceKeys: value.evidenceKeys,
        taskRefs: references(value.taskRefs),
        objectiveRefs: [],
      })),
      leaderDecisions: content.leaderDecisions.map((value: any) => ({
        question: value.questionTemplate,
        impact: value.impactTemplate,
        evidenceKeys: value.evidenceKeys,
        taskRefs: references(value.taskRefs),
        objectiveRefs: [],
      })),
      limitations: content.limitations.map(viewItem),
    },
  };
}

function dashboardResponse() {
  return {
    generatedAt: '2026-07-28T09:00:00',
    groupId: 1,
    groupName: group.name,
    timezone: group.timezone,
    visibility: 'MEMBERS',
    periodFrom: '2026-07-01',
    periodTo: '2026-07-31',
    totalCount: 4,
    statuses: {
      requested: 0, todo: 1, inProgress: 1, onHold: 0,
      completed: 2, rejected: 0, cancelled: 0, delayed: 1,
    },
    workflowProgressPercent: 63,
    periodCreatedCount: 4,
    periodCompletedCount: 2,
    periodCompletionRatePercent: 50,
    completedWithDueDateCount: 3,
    onTimeCompletedCount: 2,
    onTimeRatePercent: 67,
    averageCompletionHours: 18,
    members: [],
    riskTasks: [],
    periodTasks: [],
    calendarItems: [],
  };
}

function json(route: Route, body: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(body),
  });
}

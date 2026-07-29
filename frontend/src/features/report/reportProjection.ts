import type {
  CompletedWeeklyAiReport,
  MemberWorkView,
  RiskNarrativeItemView,
  TaskWorkView,
} from '../../api/reportApi';
import type { ReportDensity } from './components/AiReportContent';

export type ReportScope = 'GROUP' | 'MEMBER_COMPARISON' | 'INDIVIDUAL_MEMBER';

export type ReportProjectionState = {
  scope: ReportScope;
  density: ReportDensity;
  memberRef?: string;
};

type ServerRiskSignal = CompletedWeeklyAiReport['metrics']['riskSignals'][number];
type KnownServerRiskCode =
  'OVERDUE_PRESENT'
  | 'ON_HOLD_PRESENT'
  | 'HIGH_PRIORITY_PRESENT';

export type ProjectedServerRisk = {
  signal: ServerRiskSignal;
  frozenIndex: number;
  known: boolean;
  tasks: readonly TaskWorkView[];
};

export type ProjectedAiRisk = {
  item: RiskNarrativeItemView;
  frozenIndex: number;
  tasks: readonly TaskWorkView[];
  taskRefMismatch: boolean;
};

export type ProjectedServerRiskTask = {
  task: TaskWorkView;
  risks: readonly ProjectedServerRisk[];
  representativeRisk: ProjectedServerRisk;
};

export type ProjectedMemberException = {
  member: MemberWorkView;
  tasks: readonly TaskWorkView[];
  representativeRisk: ProjectedServerRisk;
};

export type ProjectedMemberRiskWork = {
  member: MemberWorkView;
  matchedRiskTasks: readonly ProjectedServerRiskTask[];
  assignedTasks: readonly TaskWorkView[];
  representativeRisk?: ProjectedServerRisk;
};

export type ReportRiskProjection = {
  serverRisks: readonly ProjectedServerRisk[];
  aiRisks: readonly ProjectedAiRisk[];
  serverRiskTasks: readonly ProjectedServerRiskTask[];
  memberExceptions: readonly ProjectedMemberException[];
  memberRiskWork: readonly ProjectedMemberRiskWork[];
};

const SERVER_RISK_RULES: Record<KnownServerRiskCode, {
  evidenceKey: string;
  matches: (task: TaskWorkView) => boolean;
}> = {
  OVERDUE_PRESENT: {
    evidenceKey: 'tasks.delayed',
    matches: (task) => task.dueState === 'OVERDUE',
  },
  ON_HOLD_PRESENT: {
    evidenceKey: 'tasks.onHold',
    matches: (task) => task.status === 'ON_HOLD',
  },
  HIGH_PRIORITY_PRESENT: {
    evidenceKey: 'tasks.highPriority',
    matches: (task) => task.priority === 'HIGH' || task.priority === 'URGENT',
  },
};
const SERVER_RISK_CODE_PRECEDENCE: Record<KnownServerRiskCode, number> = {
  OVERDUE_PRESENT: 0,
  ON_HOLD_PRESENT: 1,
  HIGH_PRIORITY_PRESENT: 2,
};
const RISK_SEVERITY_PRECEDENCE: Record<ServerRiskSignal['severity'], number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
};

export const DEFAULT_REPORT_PROJECTION_STATE: ReportProjectionState = {
  scope: 'GROUP',
  density: 'STANDARD',
};

export function readReportProjectionState(
  searchParams: URLSearchParams,
): ReportProjectionState {
  const scope = reportScope(searchParams.get('scope'));
  const density = reportDensity(searchParams.get('density'));
  const memberRef = searchParams.get('memberRef') || undefined;
  return normalizeReportProjectionState({ scope, density, memberRef });
}

export function normalizeReportProjectionState(
  state: ReportProjectionState,
  frozenMembers?: readonly MemberWorkView[],
): ReportProjectionState {
  if (state.scope !== 'INDIVIDUAL_MEMBER') {
    return { scope: state.scope, density: state.density };
  }
  if (!state.memberRef
    || (frozenMembers
      && !frozenMembers.some(({ member }) => member.ref === state.memberRef))) {
    return { scope: 'GROUP', density: state.density };
  }
  return state;
}

export function writeReportProjectionState(
  current: URLSearchParams,
  state: ReportProjectionState,
): URLSearchParams {
  const next = new URLSearchParams(current);
  if (state.scope === 'GROUP') next.delete('scope');
  else next.set('scope', state.scope);
  if (state.density === 'STANDARD') next.delete('density');
  else next.set('density', state.density);
  if (state.scope === 'INDIVIDUAL_MEMBER' && state.memberRef) {
    next.set('memberRef', state.memberRef);
  } else {
    next.delete('memberRef');
  }
  return next;
}

export function sameReportProjectionState(
  left: ReportProjectionState,
  right: ReportProjectionState,
) {
  return left.scope === right.scope
    && left.density === right.density
    && left.memberRef === right.memberRef;
}

export function projectReportRisks(
  report: CompletedWeeklyAiReport,
): ReportRiskProjection {
  const frozenTasks = uniqueTasks(report.operations.tasks);
  const serverRisks = report.metrics.riskSignals
    .map((signal, frozenIndex): ProjectedServerRisk => {
      const rule = serverRiskRule(signal.code);
      const tasks = rule && signal.evidenceKeys.includes(rule.evidenceKey)
        ? frozenTasks.filter(rule.matches)
        : [];
      return {
        signal,
        frozenIndex,
        known: Boolean(rule),
        tasks,
      };
    })
    .sort(compareServerRisks);
  const aiRisks = report.analysis.risks.map((item, frozenIndex): ProjectedAiRisk => {
    if (item.taskRefs.length === 0) {
      return { item, frozenIndex, tasks: [], taskRefMismatch: false };
    }
    const referencedTaskRefs = new Set(item.taskRefs.map(({ ref }) => ref));
    const tasks = frozenTasks.filter(({ task }) => referencedTaskRefs.has(task.ref));
    return {
      item,
      frozenIndex,
      tasks,
      taskRefMismatch: tasks.length === 0,
    };
  });
  const serverRiskTasks = projectServerRiskTasks(frozenTasks, serverRisks);
  const memberRiskWork = projectMemberRiskWork(
    report.operations.members,
    report.operations.tasks,
    serverRiskTasks,
  );
  return {
    serverRisks,
    aiRisks,
    serverRiskTasks,
    memberRiskWork,
    memberExceptions: projectMemberExceptions(
      report.operations.members,
      serverRiskTasks,
    ),
  };
}

function projectMemberRiskWork(
  frozenMembers: readonly MemberWorkView[],
  frozenTasks: readonly TaskWorkView[],
  serverRiskTasks: readonly ProjectedServerRiskTask[],
): ProjectedMemberRiskWork[] {
  return frozenMembers.map((member) => {
    const assignedTasks = frozenTasks.filter(({ assignee }) =>
      assignee?.ref === member.member.ref);
    const matchedRiskTasks = serverRiskTasks.filter(({ task }) =>
      task.assignee?.ref === member.member.ref);
    const representativeRisk = matchedRiskTasks.reduce<ProjectedServerRisk | undefined>(
      (current, item) => !current
        || compareRiskPriority(item.representativeRisk, current) < 0
        ? item.representativeRisk
        : current,
      undefined,
    );
    return { member, matchedRiskTasks, assignedTasks, representativeRisk };
  });
}

function projectServerRiskTasks(
  frozenTasks: readonly TaskWorkView[],
  serverRisks: readonly ProjectedServerRisk[],
): ProjectedServerRiskTask[] {
  return frozenTasks.flatMap((task) => {
    const risks = serverRisks.filter((risk) =>
      risk.tasks.some(({ task: reference }) => reference.ref === task.task.ref));
    return risks.length > 0
      ? [{ task, risks, representativeRisk: risks[0] }]
      : [];
  });
}

function projectMemberExceptions(
  frozenMembers: readonly MemberWorkView[],
  serverRiskTasks: readonly ProjectedServerRiskTask[],
): ProjectedMemberException[] {
  const membersByRef = new Map(
    frozenMembers.map((member) => [member.member.ref, member]),
  );
  const candidates = new Map<string, {
    member: MemberWorkView;
    tasks: Map<string, TaskWorkView>;
    representativeRisk: ProjectedServerRisk;
  }>();
  for (const taskRisk of serverRiskTasks) {
    const assigneeRef = taskRisk.task.assignee?.ref;
    if (!assigneeRef) continue;
    const member = membersByRef.get(assigneeRef);
    if (!member) continue;
    const existing = candidates.get(assigneeRef);
    if (!existing) {
      candidates.set(assigneeRef, {
        member,
        tasks: new Map([[taskRisk.task.task.ref, taskRisk.task]]),
        representativeRisk: taskRisk.representativeRisk,
      });
      continue;
    }
    existing.tasks.set(taskRisk.task.task.ref, taskRisk.task);
    if (compareRiskPriority(
      taskRisk.representativeRisk,
      existing.representativeRisk,
    ) < 0) {
      existing.representativeRisk = taskRisk.representativeRisk;
    }
  }
  return [...candidates.values()]
    .map(({ member, tasks, representativeRisk }) => ({
      member,
      tasks: [...tasks.values()],
      representativeRisk,
    }))
    .sort(compareMemberExceptions)
    .slice(0, 3);
}

function compareMemberExceptions(
  left: ProjectedMemberException,
  right: ProjectedMemberException,
) {
  const riskClass = compareRiskClass(
    left.representativeRisk,
    right.representativeRisk,
  );
  if (riskClass !== 0) return riskClass;
  if (left.tasks.length !== right.tasks.length) {
    return right.tasks.length - left.tasks.length;
  }
  if (left.member.delayed !== right.member.delayed) {
    return right.member.delayed - left.member.delayed;
  }
  return compareOrdinal(left.member.member.ref, right.member.member.ref);
}

function compareServerRisks(
  left: ProjectedServerRisk,
  right: ProjectedServerRisk,
) {
  if (left.known !== right.known) return left.known ? -1 : 1;
  if (!left.known) return left.frozenIndex - right.frozenIndex;
  return compareRiskPriority(left, right);
}

function compareRiskPriority(
  left: ProjectedServerRisk,
  right: ProjectedServerRisk,
) {
  const riskClass = compareRiskClass(left, right);
  return riskClass !== 0 ? riskClass : left.frozenIndex - right.frozenIndex;
}

function compareRiskClass(
  left: ProjectedServerRisk,
  right: ProjectedServerRisk,
) {
  const severity = RISK_SEVERITY_PRECEDENCE[left.signal.severity]
    - RISK_SEVERITY_PRECEDENCE[right.signal.severity];
  if (severity !== 0) return severity;
  return serverRiskCodePrecedence(left.signal.code)
    - serverRiskCodePrecedence(right.signal.code);
}

function serverRiskRule(code: string) {
  return isKnownServerRiskCode(code) ? SERVER_RISK_RULES[code] : undefined;
}

function serverRiskCodePrecedence(code: string) {
  return isKnownServerRiskCode(code)
    ? SERVER_RISK_CODE_PRECEDENCE[code]
    : Number.MAX_SAFE_INTEGER;
}

function isKnownServerRiskCode(code: string): code is KnownServerRiskCode {
  return Object.prototype.hasOwnProperty.call(SERVER_RISK_RULES, code);
}

function uniqueTasks(tasks: readonly TaskWorkView[]) {
  const tasksByRef = new Map<string, TaskWorkView>();
  for (const task of tasks) {
    if (!tasksByRef.has(task.task.ref)) tasksByRef.set(task.task.ref, task);
  }
  return [...tasksByRef.values()];
}

function compareOrdinal(left: string, right: string) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function reportScope(value: string | null): ReportScope {
  return value === 'MEMBER_COMPARISON' || value === 'INDIVIDUAL_MEMBER'
    ? value
    : 'GROUP';
}

function reportDensity(value: string | null): ReportDensity {
  return value === 'SUMMARY' || value === 'DETAILED' ? value : 'STANDARD';
}

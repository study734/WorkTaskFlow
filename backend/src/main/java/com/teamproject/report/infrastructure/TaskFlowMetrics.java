package com.teamproject.report.infrastructure;

import com.teamproject.task.application.TaskReportDataQuery.ActivityEvent;
import com.teamproject.task.application.TaskReportDataQuery.EventType;
import com.teamproject.task.application.TaskReportDataQuery.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 활동 이벤트 시퀀스에서 상태별 체류 시간과 정체 신호를 계산한다. 어떤 화면도 보여주지 않던 값들이므로
 * 팀장이 스스로 찾기 어려운 병목의 수치 척도가 된다.
 *
 * <p>이벤트의 {@code taskStatus}는 그 변경 <em>이후</em>의 상태이므로, 한 이벤트부터 다음 이벤트까지가
 * 그 상태의 체류 구간이다. 마지막 구간은 기간 종료 시각에서 끊어 동결 값이 재현 가능하도록 한다.
 * {@code occurredAt}은 UTC로 일관 저장되고 두 값의 차이만 쓰므로 서버 시간대 문제가 없다.
 */
final class TaskFlowMetrics {
    private TaskFlowMetrics() {}

    record TaskFlow(
            long blockedHours,
            long approvalWaitHours,
            long startLagHours,
            int reopenCount,
            int assigneeChangeCount,
            long idleDays) {

        boolean hasSignal() {
            return blockedHours > 0 || approvalWaitHours > 0 || startLagHours > 0
                    || reopenCount > 0 || assigneeChangeCount > 0 || idleDays > 0;
        }
    }

    /**
     * @param history 기간 내 활동이 있던 업무의 전체 이벤트(기간 이전 포함), 시각 오름차순
     * @param boundary 기간 종료 시각. 열린 구간을 여기서 끊는다
     */
    static Map<Long, TaskFlow> byTask(List<ActivityEvent> history, Instant boundary) {
        Map<Long, List<ActivityEvent>> byTask = new LinkedHashMap<>();
        for (ActivityEvent event : history) {
            byTask.computeIfAbsent(event.taskId(), key -> new ArrayList<>()).add(event);
        }
        Map<Long, TaskFlow> result = new LinkedHashMap<>();
        byTask.forEach((taskId, events) -> result.put(taskId, flow(events, boundary)));
        return result;
    }

    private static TaskFlow flow(List<ActivityEvent> events, Instant boundary) {
        long blocked = 0;
        long approval = 0;
        long startLag = 0;
        int reopened = 0;
        int assigneeChanges = 0;
        Status previous = null;
        for (int index = 0; index < events.size(); index++) {
            ActivityEvent event = events.get(index);
            Instant from = event.occurredAt();
            Instant to = index + 1 < events.size()
                    ? events.get(index + 1).occurredAt() : boundary;
            long hours = to.isAfter(from) ? Duration.between(from, to).toHours() : 0;
            switch (event.taskStatus()) {
                case ON_HOLD -> blocked += hours;
                case REQUESTED -> approval += hours;
                case TODO -> startLag += hours;
                default -> { }
            }
            if (previous == Status.COMPLETED && event.taskStatus() == Status.IN_PROGRESS) {
                reopened++;
            }
            if (event.eventType() == EventType.ASSIGNEE_CHANGED) assigneeChanges++;
            previous = event.taskStatus();
        }
        Instant last = events.getLast().occurredAt();
        long idleDays = boundary.isAfter(last) ? Duration.between(last, boundary).toDays() : 0;
        return new TaskFlow(blocked, approval, startLag, reopened, assigneeChanges, idleDays);
    }
}

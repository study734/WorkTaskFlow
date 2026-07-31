package com.teamproject.report.application;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Snapshot 조립에 필요한 협업·일정 근거를 <b>bulk로만</b> 조회하는 경계다. 업무 단위 반복
 * 조회를 금지한다(v7-2 §3). 업무 수가 늘어도 쿼리 횟수는 상수여야 한다.
 *
 * <p>여기서 나오는 값은 전부 집계 수치이거나 구조화 코드다. 댓글 원문·설명 원문·실명은
 * 반환하지 않는다.
 */
public interface AiWeeklyReportEvidenceQuery {

    /**
     * 업무별 협업 집계. 결과에 없는 taskId는 모두 0으로 간주한다.
     * 호출 1회당 고정된 수의 쿼리만 실행한다.
     */
    Map<Long, TaskCollaborationCounts> loadCollaborationCounts(Collection<Long> taskIds);

    /** 기간과 겹치는 그룹 일정. 표시 문자열이 아니라 종류·시각·소유자만 담는다. */
    List<CalendarWindow> loadCalendarWindows(
            Long groupId, LocalDateTime fromUtc, LocalDateTime toExclusiveUtc);

    record TaskCollaborationCounts(
            int commentCount, int unresolvedMentionCount, int resourceLinkCount) {

        public static final TaskCollaborationCounts NONE = new TaskCollaborationCounts(0, 0, 0);
    }

    /**
     * @param eventType  구조화 종류. 제목은 담지 않는다.
     * @param ownerMemberId 일정을 만든 팀원. 표시 이름이 아니라 id다.
     */
    record CalendarWindow(
            Long eventId,
            String eventType,
            boolean allDay,
            LocalDateTime startAtUtc,
            LocalDateTime endAtUtc,
            Long ownerMemberId) {}
}

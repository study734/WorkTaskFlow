package com.teamproject.report.infrastructure;

import com.teamproject.calendar.domain.CalendarEvent;
import com.teamproject.calendar.domain.CalendarEventRepository;
import com.teamproject.report.application.AiWeeklyReportEvidenceQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업무 단위 반복 조회 대신 taskId 목록 기반 집계 쿼리만 실행한다. 업무 100건이든 1건이든
 * 쿼리 횟수는 같다.
 *
 * <p>기존 {@code TaskCommentRepository.findAllByTaskId...}, {@code GroupResourceRepository
 * .findAllByTaskId...}는 업무당 1회 조회라 Snapshot 조립에 쓰면 N+1이 된다. 그래서 별도
 * 집계 경로를 둔다.
 */
@Repository
public class TaskEvidenceQueryRepository implements AiWeeklyReportEvidenceQuery {

    private static final String COMMENT_COUNTS = """
            select comment.task.id, count(comment.id)
            from TaskComment comment
            where comment.task.id in :taskIds
              and comment.deletedAt is null
            group by comment.task.id
            """;

    /**
     * 멘션 대상자가 그 멘션 이후로 해당 업무에 댓글을 달지 않았으면 아직 응답이 없는 것으로
     * 본다. 별도 resolved 플래그가 도메인에 없으므로 관측 가능한 사실로 판정한다.
     */
    private static final String UNRESOLVED_MENTION_COUNTS = """
            select mention.comment.task.id, count(mention.id)
            from CommentMention mention
            where mention.comment.task.id in :taskIds
              and mention.comment.deletedAt is null
              and not exists (
                  select reply.id from TaskComment reply
                  where reply.task.id = mention.comment.task.id
                    and reply.author.id = mention.mentionedMember.id
                    and reply.deletedAt is null
                    and reply.createdAt > mention.createdAt
              )
            group by mention.comment.task.id
            """;

    private static final String RESOURCE_COUNTS = """
            select resource.task.id, count(resource.id)
            from GroupResource resource
            where resource.task.id in :taskIds
              and resource.deletedAt is null
            group by resource.task.id
            """;

    private final CalendarEventRepository calendarEvents;

    @PersistenceContext
    private EntityManager entityManager;

    public TaskEvidenceQueryRepository(CalendarEventRepository calendarEvents) {
        this.calendarEvents = calendarEvents;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, TaskCollaborationCounts> loadCollaborationCounts(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Map.of();
        List<Long> ids = List.copyOf(taskIds);

        Map<Long, Long> comments = countBy(COMMENT_COUNTS, ids);
        Map<Long, Long> mentions = countBy(UNRESOLVED_MENTION_COUNTS, ids);
        Map<Long, Long> resources = countBy(RESOURCE_COUNTS, ids);

        Map<Long, TaskCollaborationCounts> result = new LinkedHashMap<>();
        for (Long taskId : ids) {
            result.put(taskId, new TaskCollaborationCounts(
                    intValue(comments.get(taskId)),
                    intValue(mentions.get(taskId)),
                    intValue(resources.get(taskId))));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarWindow> loadCalendarWindows(
            Long groupId, LocalDateTime fromUtc, LocalDateTime toExclusiveUtc) {
        // 저장소 규약상 (toUtc, fromUtc) 순서다. 겹치는 구간을 찾는 조건이라 인자가 교차한다.
        List<CalendarEvent> events = calendarEvents
                .findAllByGroupIdAndStartAtUtcLessThanAndEndAtUtcGreaterThanOrderByStartAtUtcAscIdAsc(
                        groupId, toExclusiveUtc, fromUtc);
        return events.stream()
                .map(event -> new CalendarWindow(
                        event.getId(),
                        event.getType() == null ? null : event.getType().name(),
                        event.isAllDay(),
                        event.getStartAtUtc(),
                        event.getEndAtUtc(),
                        event.getCreatedBy() == null ? null : event.getCreatedBy().getId()))
                .toList();
    }

    private Map<Long, Long> countBy(String jpql, List<Long> taskIds) {
        List<Object[]> rows = entityManager.createQuery(jpql, Object[].class)
                .setParameter("taskIds", taskIds)
                .getResultList();
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private int intValue(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}

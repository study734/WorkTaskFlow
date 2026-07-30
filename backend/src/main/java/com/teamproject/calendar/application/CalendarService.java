package com.teamproject.calendar.application;

import com.teamproject.calendar.application.dto.CalendarDtos.*;
import com.teamproject.calendar.domain.CalendarEvent;
import com.teamproject.calendar.domain.CalendarEventRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CalendarService {
    private final CalendarEventRepository events;
    private final TaskRepository tasks;
    private final GroupMemberRepository members;
    private final GroupAuthorization authorization;
    private final long rejectedTaskRetentionHours;

    public CalendarService(CalendarEventRepository events, TaskRepository tasks,
            GroupMemberRepository members, GroupAuthorization authorization,
            @Value("${app.calendar.rejected-task-retention-hours:24}") long rejectedTaskRetentionHours) {
        this.events = events;
        this.tasks = tasks;
        this.members = members;
        this.authorization = authorization;
        this.rejectedTaskRetentionHours = Math.max(0, rejectedTaskRetentionHours);
    }

    @Transactional(readOnly = true)
    public CalendarResponse list(Long userId, Long groupId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<GroupMember> memberships = groupId == null
                ? members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(userId, GroupMember.Status.ACTIVE)
                : List.of(authorization.requireActiveMember(groupId, userId));
        var items = new ArrayList<CalendarItemResponse>();
        memberships.forEach(member -> addGroupItems(items, member.getGroup(), from, to));
        items.sort(Comparator.comparing(CalendarItemResponse::startAtUtc)
                .thenComparing(CalendarItemResponse::source)
                .thenComparing(item -> item.eventId() == null ? item.sourceTaskId() : item.eventId()));
        return new CalendarResponse(items, from.toString(), to.toString());
    }

    @Transactional
    public CalendarItemResponse create(Long userId, Long groupId, CreateCalendarEventRequest request) {
        GroupMember actor = requireEditor(groupId, userId);
        Group group = actor.getGroup();
        ZoneId zone = zone(group);
        LocalDateTime startUtc = toUtc(request.startAt(), zone);
        LocalDateTime endUtc = toUtc(request.endAt(), zone);
        validateTimes(startUtc, endUtc, request.allDay(), request.startAt(), request.endAt());
        CalendarEvent event = events.save(new CalendarEvent(group, actor, type(request.type()),
                request.title().trim(), blankToNull(request.description()), startUtc, endUtc,
                request.allDay(), blankToNull(request.location())));
        return eventResponse(event);
    }

    @Transactional
    public CalendarItemResponse update(Long userId, Long eventId, UpdateCalendarEventRequest request) {
        CalendarEvent event = event(eventId);
        requireEditor(event.getGroup().getId(), userId);
        if (event.getVersion() != request.expectedVersion()) conflict();
        ZoneId zone = zone(event.getGroup());
        LocalDateTime currentStart = toLocal(event.getStartAtUtc(), zone);
        LocalDateTime currentEnd = toLocal(event.getEndAtUtc(), zone);
        LocalDateTime startLocal = request.startAt() == null ? currentStart : request.startAt();
        LocalDateTime endLocal = request.endAt() == null ? currentEnd : request.endAt();
        boolean allDay = request.allDay() == null ? event.isAllDay() : request.allDay();
        LocalDateTime startUtc = toUtc(startLocal, zone);
        LocalDateTime endUtc = toUtc(endLocal, zone);
        validateTimes(startUtc, endUtc, allDay, startLocal, endLocal);
        String title = request.title() == null ? event.getTitle() : requireTitle(request.title());
        String description = Boolean.TRUE.equals(request.clearDescription()) ? null
                : request.description() == null ? event.getDescription() : blankToNull(request.description());
        String location = Boolean.TRUE.equals(request.clearLocation()) ? null
                : request.location() == null ? event.getLocation() : blankToNull(request.location());
        CalendarEvent.Type type = request.type() == null ? event.getType() : type(request.type());
        event.update(type, title, description, startUtc, endUtc, allDay, location);
        events.flush();
        return eventResponse(event);
    }

    @Transactional
    public void delete(Long userId, Long eventId, Long expectedVersion) {
        CalendarEvent event = event(eventId);
        requireEditor(event.getGroup().getId(), userId);
        if (event.getVersion() != expectedVersion) conflict();
        events.delete(event);
        events.flush();
    }

    private void addGroupItems(List<CalendarItemResponse> items, Group group, LocalDate from, LocalDate to) {
        ZoneId zone = zone(group);
        LocalDateTime fromUtc = from.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        events.findAllByGroupIdAndStartAtUtcLessThanAndEndAtUtcGreaterThanOrderByStartAtUtcAscIdAsc(
                group.getId(), toUtc, fromUtc).stream().map(this::eventResponse).forEach(items::add);
        tasks.findAllByGroupIdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
                group.getId(), from.atStartOfDay(), to.atStartOfDay()).stream()
                .filter(task -> task.isCalendarDeadlineVisible(LocalDateTime.now(), rejectedTaskRetentionHours))
                .map(this::taskResponse).forEach(items::add);
    }

    private CalendarItemResponse eventResponse(CalendarEvent event) {
        Group group = event.getGroup();
        ZoneId zone = zone(group);
        return new CalendarItemResponse("EVENT", event.getId(), null, group.getId(), group.getName(),
                group.getType().name(), group.getTimezone(), event.getType().name(), event.getTitle(),
                event.getDescription(), toLocal(event.getStartAtUtc(), zone), toLocal(event.getEndAtUtc(), zone),
                toInstant(event.getStartAtUtc()), toInstant(event.getEndAtUtc()), event.isAllDay(),
                event.getLocation(), event.getCreatedBy().getId(), event.getVersion(),
                event.getCreatedAt(), event.getUpdatedAt(),
                event.getCreatedBy().getId(), event.getCreatedBy().getUser().getNickname());
    }

    private CalendarItemResponse taskResponse(Task task) {
        Group group = task.getGroup();
        ZoneId zone = zone(group);
        Instant dueUtc = task.getDueAt().atZone(zone).toInstant();
        GroupMember owner = task.getAssignee() == null ? task.getRequester() : task.getAssignee();
        return new CalendarItemResponse("TASK_DEADLINE", null, task.getId(), group.getId(), group.getName(),
                group.getType().name(), group.getTimezone(), "DEADLINE", task.getTitle(), task.getDescription(),
                task.getDueAt(), task.getDueAt(), dueUtc, dueUtc, false, null,
                task.getRequester().getId(), task.getVersion(), task.getCreatedAt(), task.getUpdatedAt(),
                owner.getId(), owner.getUser().getNickname());
    }

    private GroupMember requireEditor(Long groupId, Long userId) {
        return authorization.requireLeader(groupId, userId);
    }

    private CalendarEvent event(Long eventId) {
        return events.findById(eventId).orElseThrow(() -> new ApplicationException(
                "CALENDAR_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from) || from.plusDays(366).isBefore(to)) {
            throw new ApplicationException("CALENDAR_RANGE_INVALID", HttpStatus.BAD_REQUEST,
                    "조회 기간은 시작일 이후부터 최대 366일까지 입력해 주세요.");
        }
    }

    private void validateTimes(LocalDateTime startUtc, LocalDateTime endUtc, boolean allDay,
            LocalDateTime startLocal, LocalDateTime endLocal) {
        if (!endUtc.isAfter(startUtc)) {
            throw new ApplicationException("CALENDAR_TIME_INVALID", HttpStatus.BAD_REQUEST,
                    "종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        if (allDay && (!startLocal.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !endLocal.toLocalTime().equals(LocalTime.MIDNIGHT))) {
            throw new ApplicationException("CALENDAR_ALL_DAY_TIME_INVALID", HttpStatus.BAD_REQUEST,
                    "종일 일정은 날짜 경계 시각으로 입력해 주세요.");
        }
    }

    private LocalDateTime toUtc(LocalDateTime local, ZoneId zone) {
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) {
            throw new ApplicationException("CALENDAR_LOCAL_TIME_INVALID", HttpStatus.BAD_REQUEST,
                    "시간대 전환으로 존재하지 않거나 중복되는 현지 시각입니다.");
        }
        return local.atOffset(offsets.get(0)).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
    private LocalDateTime toLocal(LocalDateTime utc, ZoneId zone) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
    }
    private Instant toInstant(LocalDateTime utc) { return utc.toInstant(ZoneOffset.UTC); }
    private ZoneId zone(Group group) { return ZoneId.of(group.getTimezone()); }
    private String requireTitle(String value) {
        if (value.isBlank()) throw new ApplicationException("CALENDAR_TITLE_REQUIRED", HttpStatus.BAD_REQUEST,
                "일정 제목을 입력해 주세요.");
        return value.trim();
    }
    private CalendarEvent.Type type(String value) {
        try { return CalendarEvent.Type.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException exception) {
            throw new ApplicationException("CALENDAR_TYPE_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 일정 유형을 입력해 주세요.");
        }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void conflict() { throw new ApplicationException("CALENDAR_VERSION_CONFLICT", HttpStatus.CONFLICT,
            "일정이 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."); }
}

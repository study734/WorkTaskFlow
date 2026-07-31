package com.teamproject.report.application;

import com.teamproject.report.application.dto.AiWeeklyReportDtos.DueState;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.HoldReasonCategory;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.Language;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI로 나가는 표시 라벨을 만든다. v7-2 D2 확정에 따라 <b>원본 업무 제목과 일정 제목은
 * 전송하지 않는다</b>. 라벨은 서버가 상태·담당·마감·체크리스트·보류 신호에서 조합한 비식별
 * 의미 유형 문장이며 원문 조각을 포함하지 않는다.
 *
 * <p>이 클래스의 메서드는 <b>제목을 인자로 받지 않는다.</b> 원문 유입 경로를 시그니처
 * 수준에서 막기 위한 것이며, 실수로 제목을 넘기는 코드는 컴파일되지 않는다.
 *
 * <p>실제 제목과 이름은 분석이 끝난 뒤 renderer가 taskRef/eventRef로 다시 결합해 사용자
 * 화면과 PDF에만 표시한다.
 */
@Component
public class AiWeeklyReportSafeLabelFactory {
    /** Snapshot Schema의 tasks[].safeLabel 상한. */
    public static final int TASK_LABEL_MAX_LENGTH = 120;
    /** Snapshot Schema의 calendarConstraints[].safeLabel 상한. */
    public static final int EVENT_LABEL_MAX_LENGTH = 160;

    /** 한 라벨에 붙일 수 있는 수식어 수. 넘기면 문장이 길고 읽히지 않는다. */
    private static final int MAX_QUALIFIERS = 2;

    public String taskLabel(TaskLabelFacts facts, Language language) {
        boolean ko = language != Language.EN;
        List<String> parts = new ArrayList<>(qualifiers(facts, ko));
        parts.add(statusPhrase(facts, ko));
        String label = String.join(" ", parts.stream().filter(v -> !v.isBlank()).toList())
                + (ko ? " 업무" : " task");
        return truncate(normalize(label), TASK_LABEL_MAX_LENGTH);
    }

    public String eventLabel(String eventType, boolean allDay, Language language) {
        boolean ko = language != Language.EN;
        String kind = eventKind(eventType, ko);
        String span = allDay ? (ko ? "종일 " : "all-day ") : "";
        String label = ko
                ? "확정된 " + span + kind + " 일정"
                : "confirmed " + span + kind;
        return truncate(normalize(label), EVENT_LABEL_MAX_LENGTH);
    }

    private List<String> qualifiers(TaskLabelFacts facts, boolean ko) {
        List<String> found = new ArrayList<>();
        if (facts.status() == TaskStatus.TODO && !facts.assigned()) {
            found.add(ko ? "승인 후 담당자가 없는" : "approved but unassigned");
        }
        String hold = holdPhrase(facts.holdReasonCategory(), ko);
        if (!hold.isBlank()) found.add(hold);
        if (facts.checklistTotal() > 0 && facts.checklistCompleted() == 0) {
            found.add(ko ? "체크리스트가 시작되지 않은" : "with an unstarted checklist");
        }
        if (facts.unresolvedMentionCount() > 0) {
            found.add(ko ? "확인되지 않은 요청이 남은" : "with an unanswered request");
        }
        return found.size() <= MAX_QUALIFIERS ? found : found.subList(0, MAX_QUALIFIERS);
    }

    private String holdPhrase(HoldReasonCategory category, boolean ko) {
        if (category == null) return "";
        return switch (category) {
            case EXTERNAL_FEEDBACK -> ko ? "외부 회신을 기다리는" : "waiting on an external reply";
            case DEPENDENCY -> ko ? "선행 작업에 막힌" : "blocked by a dependency";
            case RESOURCE_SHORTAGE -> ko ? "자원이 부족한" : "short on resources";
            case PRIORITY_CHANGE -> ko ? "우선순위가 바뀐" : "reprioritised";
            case OTHER, UNKNOWN -> ko ? "사유가 기록되지 않은" : "held for an unrecorded reason";
            case NONE -> "";
        };
    }

    /** 마감 상태가 상태값보다 회의에서 더 중요하므로 먼저 본다. */
    private String statusPhrase(TaskLabelFacts facts, boolean ko) {
        DueState due = facts.dueState();
        if (due == DueState.OVERDUE) return ko ? "지연된" : "overdue";
        if (due == DueState.COMPLETED_LATE) return ko ? "기한을 넘겨 완료된" : "completed late";
        if (due == DueState.COMPLETED_ON_TIME) return ko ? "기한 내 완료된" : "completed on time";
        if (due == DueState.DUE_SOON) return ko ? "마감이 임박한" : "due soon";
        return switch (facts.status()) {
            case REQUESTED -> ko ? "승인 대기 중인" : "awaiting approval";
            case TODO -> ko ? "착수 전인" : "not started";
            case IN_PROGRESS -> ko ? "진행 중인" : "in progress";
            case ON_HOLD -> ko ? "보류 중인" : "on hold";
            case COMPLETED -> ko ? "완료된" : "completed";
            case REJECTED -> ko ? "반려된" : "rejected";
            case CANCELLED -> ko ? "취소된" : "cancelled";
        };
    }

    private String eventKind(String eventType, boolean ko) {
        String value = eventType == null ? "" : eventType.trim().toUpperCase();
        return switch (value) {
            case "MEETING" -> ko ? "회의" : "meeting";
            case "VACATION" -> ko ? "휴가" : "time off";
            case "TODO" -> ko ? "할 일" : "to-do";
            case "SCHEDULE" -> ko ? "일반" : "general";
            default -> ko ? "기타" : "other";
        };
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    /**
     * 라벨 조합에 필요한 사실만 담는다. 제목·설명·담당자 이름은 의도적으로 없다.
     *
     * @param assigned 담당자 지정 여부. 담당자가 누구인지는 담지 않는다.
     */
    public record TaskLabelFacts(
            TaskStatus status,
            DueState dueState,
            boolean assigned,
            int checklistCompleted,
            int checklistTotal,
            int unresolvedMentionCount,
            HoldReasonCategory holdReasonCategory) {}
}

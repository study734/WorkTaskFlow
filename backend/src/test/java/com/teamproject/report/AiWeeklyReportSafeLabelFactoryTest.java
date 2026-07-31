package com.teamproject.report;

import com.teamproject.report.application.AiWeeklyReportSafeLabelFactory;
import com.teamproject.report.application.AiWeeklyReportSafeLabelFactory.TaskLabelFacts;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.DueState;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.HoldReasonCategory;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.Language;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * safeLabel은 OpenAI로 나가는 유일한 표시 문자열이다. 원문 제목이 새어 나가면 v7-2의
 * 개인정보 경계가 무너지므로, 라벨이 <b>구조적으로</b> 원문을 담을 수 없다는 것까지 확인한다.
 */
class AiWeeklyReportSafeLabelFactoryTest {
    private final AiWeeklyReportSafeLabelFactory factory = new AiWeeklyReportSafeLabelFactory();

    /**
     * 가장 중요한 보장. 제목을 넘길 수 있는 인자가 아예 없으므로 실수로도 원문이 들어갈 수
     * 없다. 문자열 검사보다 강한 보장이라 시그니처를 직접 확인한다.
     */
    @Test
    @DisplayName("라벨 생성 API는 제목이나 설명을 받을 수 있는 인자를 노출하지 않는다")
    void exposesNoParameterThatCouldCarryRawText() {
        Method taskLabel = Arrays.stream(AiWeeklyReportSafeLabelFactory.class.getMethods())
                .filter(method -> method.getName().equals("taskLabel"))
                .findFirst().orElseThrow();

        assertThat(taskLabel.getParameterTypes())
                .containsExactly(TaskLabelFacts.class, Language.class);
        assertThat(TaskLabelFacts.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("title", "description", "assigneeName", "holdReason");
    }

    @Test
    @DisplayName("승인 후 미할당 지연 업무를 신호 조합으로 설명한다")
    void describesApprovedUnassignedOverdueWork() {
        String label = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.TODO, DueState.OVERDUE, false, 0, 5, 0,
                HoldReasonCategory.NONE), Language.KO);

        assertThat(label).isEqualTo("승인 후 담당자가 없는 체크리스트가 시작되지 않은 지연된 업무");
    }

    @Test
    @DisplayName("보류 사유는 구조화 category만 문장으로 옮긴다")
    void describesHoldReasonFromCategoryOnly() {
        String label = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.ON_HOLD, DueState.UPCOMING, true, 1, 6, 0,
                HoldReasonCategory.EXTERNAL_FEEDBACK), Language.KO);

        assertThat(label).isEqualTo("외부 회신을 기다리는 보류 중인 업무");
    }

    @Test
    @DisplayName("마감 상태를 업무 상태보다 앞세운다")
    void prefersDueStateOverStatus() {
        String dueSoon = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.IN_PROGRESS, DueState.DUE_SOON, true, 2, 4, 0,
                HoldReasonCategory.NONE), Language.KO);
        String noDue = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.IN_PROGRESS, DueState.NO_DUE, true, 2, 4, 0,
                HoldReasonCategory.NONE), Language.KO);

        assertThat(dueSoon).isEqualTo("마감이 임박한 업무");
        assertThat(noDue).isEqualTo("진행 중인 업무");
    }

    @Test
    @DisplayName("수식어는 최대 2개까지만 붙인다")
    void keepsAtMostTwoQualifiers() {
        String label = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.TODO, DueState.OVERDUE, false, 0, 3, 2,
                HoldReasonCategory.DEPENDENCY), Language.KO);

        assertThat(label).isEqualTo("승인 후 담당자가 없는 선행 작업에 막힌 지연된 업무");
        assertThat(label).doesNotContain("확인되지 않은 요청이 남은");
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    @DisplayName("모든 업무 상태가 빈 라벨 없이 문장을 만든다")
    void producesLabelForEveryStatus(TaskStatus status) {
        String label = factory.taskLabel(new TaskLabelFacts(
                status, DueState.NO_DUE, true, 0, 0, 0,
                HoldReasonCategory.NONE), Language.KO);

        assertThat(label).isNotBlank().endsWith("업무").doesNotContain("  ");
    }

    @ParameterizedTest
    @EnumSource(HoldReasonCategory.class)
    @DisplayName("모든 보류 category가 라벨 길이 상한 안에서 처리된다")
    void handlesEveryHoldReasonWithinLimit(HoldReasonCategory category) {
        String label = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.ON_HOLD, DueState.OVERDUE, false, 0, 9, 3, category), Language.KO);

        assertThat(label).isNotBlank()
                .hasSizeLessThanOrEqualTo(
                        AiWeeklyReportSafeLabelFactory.TASK_LABEL_MAX_LENGTH);
    }

    @Test
    @DisplayName("영문 리포트는 영문 라벨을 만든다")
    void buildsEnglishLabel() {
        String label = factory.taskLabel(new TaskLabelFacts(
                TaskStatus.TODO, DueState.OVERDUE, false, 0, 5, 0,
                HoldReasonCategory.NONE), Language.EN);

        assertThat(label)
                .isEqualTo("approved but unassigned with an unstarted checklist overdue task");
    }

    @Test
    @DisplayName("일정 라벨은 종류와 종일 여부만 사용한다")
    void buildsEventLabelFromTypeOnly() {
        assertThat(factory.eventLabel("MEETING", false, Language.KO))
                .isEqualTo("확정된 회의 일정");
        assertThat(factory.eventLabel("VACATION", true, Language.KO))
                .isEqualTo("확정된 종일 휴가 일정");
        assertThat(factory.eventLabel("SCHEDULE", false, Language.EN))
                .isEqualTo("confirmed general");
    }

    @Test
    @DisplayName("알 수 없는 일정 종류도 빈 라벨을 만들지 않는다")
    void fallsBackForUnknownEventType() {
        assertThat(factory.eventLabel(null, false, Language.KO)).isEqualTo("확정된 기타 일정");
        assertThat(factory.eventLabel("WHATEVER", false, Language.KO))
                .isEqualTo("확정된 기타 일정");
    }
}

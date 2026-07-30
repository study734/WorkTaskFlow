package com.teamproject.report;

import com.teamproject.report.application.MemberPerformanceRule;
import com.teamproject.report.application.MemberPerformanceRule.Rating;
import com.teamproject.report.application.ReportContracts.MemberMetric;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPerformanceRuleTest {

    @Test
    void gradesOnCompletionRateBandBoundaries() {
        assertThat(gradeOf(85)).isEqualTo("A");
        assertThat(gradeOf(84)).isEqualTo("B");
        assertThat(gradeOf(70)).isEqualTo("B");
        assertThat(gradeOf(69)).isEqualTo("C");
        assertThat(gradeOf(55)).isEqualTo("C");
        assertThat(gradeOf(54)).isEqualTo("D");
        assertThat(gradeOf(40)).isEqualTo("D");
        assertThat(gradeOf(39)).isEqualTo("E");
    }

    // 배정 업무가 없는 팀원을 0점 E로 떨어뜨리면 "일이 없었다"가 "성과가 나쁘다"로 뒤바뀐다.
    @Test
    void marksMemberWithoutAssignedWorkAsNotRated() {
        Rating rating = rate(new MemberMetric("MEMBER-01", 0, 0, 0, 0, null, 0, 0, 0, null));

        assertThat(rating.grade()).isEqualTo(MemberPerformanceRule.NOT_RATED);
        assertThat(rating.score()).isNull();
        assertThat(rating.rank()).isNull();
        assertThat(rating.rated()).isFalse();
    }

    @Test
    void marksMemberWithoutAnyComputedRateAsNotRated() {
        Rating rating = rate(new MemberMetric("MEMBER-01", 4, 4, 0, 0, null, 0, 0, 0, null));

        assertThat(rating.grade()).isEqualTo(MemberPerformanceRule.NOT_RATED);
    }

    @Test
    void capsDelayAndOnHoldPenalties() {
        // 완료율 100, 전부 지연, 보류 5건 → 감점은 20 + 10으로 상한에서 멈춘다.
        Rating rating = rate(new MemberMetric("MEMBER-01", 4, 4, 4, 4, null, 5, 0, 0, 100));

        assertThat(rating.score()).isEqualTo(70);
        assertThat(rating.grade()).isEqualTo("B");
    }

    @Test
    void weightsOnlyTheRatesThatExist() {
        // 완료율 80 + 기한 준수율 60 → (45*80 + 30*60) / 75 = 72
        Rating rating = rate(new MemberMetric("MEMBER-01", 5, 0, 4, 0, 60, 0, 0, 0, 80));

        assertThat(rating.score()).isEqualTo(72);
    }

    @Test
    void includesChecklistRateWhenItemsExist() {
        // 체크리스트 3/4 → 75. 완료율 100과 함께 (45*100 + 25*75) / 70 = 91
        Rating rating = rate(new MemberMetric("MEMBER-01", 2, 0, 2, 0, null, 0, 4, 3, 100));

        assertThat(rating.score()).isEqualTo(91);
        assertThat(MemberPerformanceRule.checklistRate(
                new MemberMetric("MEMBER-01", 2, 0, 2, 0, null, 0, 4, 3, 100))).isEqualTo(75);
    }

    @Test
    void ranksTiedScoresWithStandardCompetitionRanking() {
        Map<String, Rating> ratings = MemberPerformanceRule.rate(List.of(
                member("MEMBER-01", 90),
                member("MEMBER-02", 80),
                member("MEMBER-03", 80),
                member("MEMBER-04", 70)));

        assertThat(ratings.get("MEMBER-01").rank()).isEqualTo("1/4");
        assertThat(ratings.get("MEMBER-02").rank()).isEqualTo("2/4");
        assertThat(ratings.get("MEMBER-03").rank()).isEqualTo("2/4");
        assertThat(ratings.get("MEMBER-04").rank()).isEqualTo("4/4");
    }

    @Test
    void excludesNotRatedMembersFromRanking() {
        Map<String, Rating> ratings = MemberPerformanceRule.rate(List.of(
                member("MEMBER-01", 90),
                new MemberMetric("MEMBER-02", 0, 0, 0, 0, null, 0, 0, 0, null),
                member("MEMBER-03", 60)));

        assertThat(ratings.get("MEMBER-01").rank()).isEqualTo("1/2");
        assertThat(ratings.get("MEMBER-02").rank()).isNull();
        assertThat(ratings.get("MEMBER-03").rank()).isEqualTo("2/2");
    }

    @Test
    void producesTheSameResultRegardlessOfInputOrder() {
        List<MemberMetric> members = List.of(
                member("MEMBER-01", 90), member("MEMBER-02", 80), member("MEMBER-03", 70));
        List<MemberMetric> reversed = members.reversed();

        Map<String, Rating> first = MemberPerformanceRule.rate(members);
        Map<String, Rating> second = MemberPerformanceRule.rate(reversed);

        assertThat(first.keySet()).containsExactly("MEMBER-01", "MEMBER-02", "MEMBER-03");
        for (String label : first.keySet()) {
            assertThat(second.get(label).grade()).isEqualTo(first.get(label).grade());
            assertThat(second.get(label).score()).isEqualTo(first.get(label).score());
            assertThat(second.get(label).rank()).isEqualTo(first.get(label).rank());
        }
    }

    @Test
    void describesTheRuleInBothLanguages() {
        assertThat(MemberPerformanceRule.describe("ko")).contains("완료율", "NOT_RATED");
        assertThat(MemberPerformanceRule.describe("en")).contains("completion rate", "NOT_RATED");
    }

    private String gradeOf(int completionRate) {
        return rate(member("MEMBER-01", completionRate)).grade();
    }

    private Rating rate(MemberMetric member) {
        return MemberPerformanceRule.rate(List.of(member)).get(member.memberLabel());
    }

    // 완료율만 존재하면 가중 평균이 그 값이 되므로 점수가 곧 완료율이다.
    private MemberMetric member(String label, int completionRate) {
        return new MemberMetric(label, 4, 0, 2, 0, null, 0, 0, 0, completionRate);
    }
}

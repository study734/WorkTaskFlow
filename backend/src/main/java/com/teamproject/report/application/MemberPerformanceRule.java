package com.teamproject.report.application;

import com.teamproject.report.application.ReportContracts.MemberMetric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 동결된 팀원 지표에서 성과 등급·점수·순위를 결정적으로 계산한다. AI는 이 값을 계산하지도 바꾸지도
 * 못하고 근거로 인용해 설명만 한다.
 *
 * <p>부동소수를 쓰지 않는다 — 같은 입력이 JVM·플랫폼과 무관하게 같은 등급을 내야 동결 리포트가
 * 재현된다. 입력 순서에도 의존하지 않는다.
 *
 * <p>{@code NOT_RATED}는 이 규칙의 핵심이다. 배정 업무가 없거나 산출 근거가 없는 팀원을 0점 E로
 * 떨어뜨리면 "일이 없었다"가 "성과가 나쁘다"로 뒤바뀐다.
 */
public final class MemberPerformanceRule {
    public static final String NOT_RATED = "NOT_RATED";

    private static final int COMPLETION_WEIGHT = 45;
    private static final int ON_TIME_WEIGHT = 30;
    private static final int CHECKLIST_WEIGHT = 25;
    private static final int DELAY_PENALTY_CAP = 20;
    private static final int ON_HOLD_PENALTY_CAP = 10;
    private static final int ON_HOLD_PENALTY_STEP = 5;

    private MemberPerformanceRule() {}

    public record Rating(String memberLabel, Integer score, String grade, String rank) {
        public boolean rated() { return !NOT_RATED.equals(grade); }
    }

    public static Map<String, Rating> rate(List<MemberMetric> members) {
        if (members == null || members.isEmpty()) return Map.of();
        List<Rating> scored = new ArrayList<>();
        for (MemberMetric member : members) {
            Integer score = score(member);
            scored.add(new Rating(member.memberLabel(), score,
                    score == null ? NOT_RATED : grade(score), null));
        }
        return withRanks(scored, members);
    }

    /** 산출 근거가 하나도 없으면 null이다. 이것이 NOT_RATED로 이어진다. */
    static Integer score(MemberMetric member) {
        if (member.assigned() == 0) return null;
        int weighted = 0;
        int weight = 0;
        if (member.completionRatePercent() != null) {
            weighted += COMPLETION_WEIGHT * member.completionRatePercent();
            weight += COMPLETION_WEIGHT;
        }
        if (member.onTimeRatePercent() != null) {
            weighted += ON_TIME_WEIGHT * member.onTimeRatePercent();
            weight += ON_TIME_WEIGHT;
        }
        Integer checklistRate = checklistRate(member);
        if (checklistRate != null) {
            weighted += CHECKLIST_WEIGHT * checklistRate;
            weight += CHECKLIST_WEIGHT;
        }
        if (weight == 0) return null;
        long base = rounded(weighted, weight);
        long delayShare = rounded(100L * member.delayed(), member.assigned());
        long penalty = Math.min(DELAY_PENALTY_CAP, delayShare / 2)
                + Math.min(ON_HOLD_PENALTY_CAP, ON_HOLD_PENALTY_STEP * member.onHold());
        return (int) Math.clamp(base - penalty, 0, 100);
    }

    public static Integer checklistRate(MemberMetric member) {
        if (member.checklistTotal() == 0) return null;
        return (int) rounded(100L * member.checklistCompleted(), member.checklistTotal());
    }

    /** 반올림 나눗셈을 정수만으로 한다 — 부동소수를 쓰면 재현성을 논증할 수 없다. */
    private static long rounded(long numerator, long denominator) {
        return (2 * numerator + denominator) / (2 * denominator);
    }

    private static String grade(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "E";
    }

    /** 평가 대상만 순위를 받는다. 동점은 표준 경쟁 순위(1, 2, 2, 4)다. */
    private static Map<String, Rating> withRanks(List<Rating> scored, List<MemberMetric> members) {
        Map<String, MemberMetric> byLabel = new LinkedHashMap<>();
        members.forEach(member -> byLabel.put(member.memberLabel(), member));
        List<Rating> rated = new ArrayList<>(scored.stream().filter(Rating::rated).toList());
        rated.sort(Comparator
                .comparingInt((Rating value) -> -value.score())
                .thenComparing(value -> -byLabel.get(value.memberLabel()).completed())
                .thenComparing(value -> byLabel.get(value.memberLabel()).delayed())
                .thenComparing(Rating::memberLabel));
        Map<String, String> ranks = new LinkedHashMap<>();
        int position = 0;
        Integer previousScore = null;
        int previousRank = 0;
        for (Rating value : rated) {
            position++;
            int rank = value.score().equals(previousScore) ? previousRank : position;
            ranks.put(value.memberLabel(), rank + "/" + rated.size());
            previousScore = value.score();
            previousRank = rank;
        }
        Map<String, Rating> result = new LinkedHashMap<>();
        for (Rating value : scored) {
            result.put(value.memberLabel(), new Rating(value.memberLabel(), value.score(),
                    value.grade(), ranks.get(value.memberLabel())));
        }
        return result;
    }

    public static String describe(String language) {
        if ("en".equals(language)) {
            return "Score weights completion rate 45, on-time rate 30 and checklist rate 25 over "
                    + "the metrics actually available, then subtracts up to 20 for the share of "
                    + "overdue work and up to 10 for on-hold work. Grades: A from 85, B from 70, "
                    + "C from 55, D from 40, E below 40. A member with no assigned work is "
                    + "NOT_RATED and is excluded from ranking.";
        }
        return "점수는 실제로 산출된 지표에 대해 완료율 45, 기한 준수율 30, 체크리스트 완료율 25의 "
                + "가중치를 적용한 뒤, 지연 업무 비중에 최대 20점, 보류 업무에 최대 10점을 감점해 "
                + "계산합니다. 등급은 85점 이상 A, 70점 이상 B, 55점 이상 C, 40점 이상 D, "
                + "40점 미만 E입니다. 배정된 업무가 없는 팀원은 NOT_RATED이며 순위에서 제외합니다.";
    }
}

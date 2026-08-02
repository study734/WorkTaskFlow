package com.teamproject.report.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 계약 코드와 내부 필드 이름을 사용자 언어로 옮기는 단일 사전.
 *
 * <p>구조화 필드는 렌더러가 이미 코드를 라벨로 바꾼다. 문제는 산문이다. 모델은 프롬프트에서 본
 * 어휘를 문장에 그대로 쓴다 — 실제로 "다수에서 OVERDUE 신호가 확인되어", "선택할
 * riskCandidates가 없습니다"가 사용자 문서에 찍혔다. ref 치환(명세 §8.1)과 같은 종류의 누출이라
 * 같은 자리에서 막는다.
 */
final class AiWeeklyReportCodeVocabulary {

    /** {ko, en}. 순서는 무의미하지만 계약 그룹별로 묶어 둔다. */
    private static final Map<String, String[]> LABELS = new LinkedHashMap<>();

    static {
        // SignalCode 15개
        put("APPROVED_UNASSIGNED", "담당자 미지정", "unassigned");
        put("REQUESTED_PENDING", "승인 대기", "awaiting approval");
        put("OVERDUE", "마감 초과", "overdue");
        put("DUE_SOON", "마감 임박", "due soon");
        put("ON_HOLD", "보류 중", "on hold");
        put("CHECKLIST_NOT_STARTED", "체크리스트 미착수", "checklist not started");
        put("CHECKLIST_STALLED", "체크리스트 정체", "checklist stalled");
        put("RESOURCE_MISSING", "관련 자료 없음", "no linked resource");
        put("UNRESOLVED_MENTION", "미응답 멘션", "unresolved mention");
        put("WORKLOAD_CONCENTRATION", "업무 편중", "workload concentration");
        put("NO_EFFORT_ESTIMATE", "예상 공수 없음", "no effort estimate");
        put("NO_COMPLETION_CRITERIA", "완료 기준 없음", "no completion criteria");
        put("CALENDAR_CONFLICT", "일정 충돌", "calendar conflict");
        put("COMPLETED", "완료됨", "completed");
        put("ON_TIME_COMPLETED", "기한 내 완료", "completed on time");

        // DecisionOptionCode 7개
        put("ASSIGN_OWNER_AND_SET_DUE", "담당자 지정과 기한 설정", "assigning an owner and a due date");
        put("DEFINE_HOLD_EXIT_CRITERIA", "보류 해제 조건 정의", "defining hold exit criteria");
        put("DEFER_SCOPE", "범위 연기", "deferring scope");
        put("APPROVE_SCOPE", "범위 승인", "approving scope");
        put("REQUEST_MORE_EVIDENCE", "추가 근거 요청", "requesting more evidence");
        put("REBALANCE_WORK", "업무 재배분", "rebalancing work");
        put("KEEP_CURRENT_PLAN", "현재 계획 유지", "keeping the current plan");

        // ExecutionStepCode 10개
        put("ASSIGN_OWNER", "담당자 지정", "assigning an owner");
        put("SET_DUE", "마감 설정", "setting a due date");
        put("START_CHECKLIST", "체크리스트 착수", "starting the checklist");
        put("LINK_RESOURCE", "관련 자료 연결", "linking a resource");
        put("RESOLVE_MENTION", "미응답 멘션 처리", "resolving mentions");
        put("SET_HOLD_EXIT_CRITERIA", "보류 해제 조건 설정", "setting hold exit criteria");
        put("RESUME_TASK", "업무 재개", "resuming the task");
        put("RECORD_SCOPE_DECISION", "범위 결정 기록", "recording the scope decision");
        put("SET_NEXT_REVIEW_DATE", "다음 점검일 지정", "setting the next review date");
        put("REBALANCE_ASSIGNEE", "담당자 재배분", "rebalancing the assignee");

        // CompletionSignalCode 9개
        put("ASSIGNEE_SET", "담당자 지정 저장", "assignee saved");
        put("DUE_AT_SET", "새 마감 저장", "new due date saved");
        put("CHECKLIST_STARTED", "체크리스트 착수", "checklist started");
        put("RESOURCE_LINKED", "관련 자료 연결", "resource linked");
        put("MENTION_RESOLVED", "미응답 멘션 처리", "mentions resolved");
        put("HOLD_STATE_RECORDED", "보류 사유 기록", "hold reason recorded");
        put("TASK_RESUMED", "업무 재개", "task resumed");
        put("SCOPE_DECISION_RECORDED", "범위 결정 기록", "scope decision recorded");
        put("NEXT_REVIEW_DATE_SET", "다음 점검일 지정", "next review date set");

        // MetricRef 8개
        put("PERIOD_TASK_COUNT", "기간 업무 수", "period task count");
        put("COMPLETION_RATE", "완료율", "completion rate");
        put("ON_TIME_RATE", "기한 준수율", "on-time rate");
        put("DELAYED_COUNT", "지연 업무 수", "delayed count");
        put("PERIOD_TASK_COUNT_DELTA", "기간 업무 수 증감", "change in period task count");
        put("COMPLETION_RATE_DELTA", "완료율 증감", "change in completion rate");
        put("ON_TIME_RATE_DELTA", "기한 준수율 증감", "change in on-time rate");
        put("DELAYED_COUNT_DELTA", "지연 업무 수 증감", "change in delayed count");

        // 역할 enum 6개. 문서 렌더러의 roleLabel과 값이 겹치지만 register가 다르다 — 구조화
        // 필드는 "Leader"처럼 이름으로 쓰고, 산문은 "the leader"처럼 문장으로 읽혀야 한다.
        put("LEADER", "팀장", "the leader");
        put("GROUP_ADMIN", "그룹 관리자", "the group admin");
        put("SELECTED_MEMBER", "지정 팀원", "the selected member");
        put("CURRENT_ASSIGNEE", "현재 담당자", "the current assignee");
        put("REQUESTER", "요청자", "the requester");
        put("TEAM", "팀 전체", "the team");

        // 프롬프트가 쓰는 내부 필드 이름. 모델이 문장에 그대로 옮겨 적는다.
        put("riskCandidates", "위험 후보", "risk candidates");
        put("missingEvidence", "부족한 근거", "missing evidence");
        put("evidenceCodes", "근거 신호", "evidence signals");
    }

    /** 긴 이름이 짧은 이름의 접두사인 경우(예: {@code COMPLETION_RATE_DELTA})가 있어 길이 순으로 만든다. */
    private static final Pattern TOKENS = Pattern.compile(
            LABELS.keySet().stream()
                    .sorted((a, b) -> b.length() - a.length())
                    .reduce((a, b) -> a + "|" + b)
                    .map(body -> "\\b(?:" + body + ")\\b")
                    .orElse("(?!)"));

    /** 치환 뒤 곧바로 오는 조사. 앞말의 받침이 바뀌므로 다시 골라야 한다. */
    private static final Pattern JOSA = Pattern.compile("^(은|는|이|가|을|를|과|와|으로|로)(?![가-힣])");

    private AiWeeklyReportCodeVocabulary() {}

    static String label(String code, boolean ko) {
        String[] pair = LABELS.get(code);
        return pair == null ? null : (ko ? pair[0] : pair[1]);
    }

    /** 문장 안의 코드 토큰을 사용자 언어로 바꾼다. 사전에 없는 대문자 단어는 건드리지 않는다. */
    static String resolveInProse(String text, boolean ko) {
        if (text == null || text.isEmpty()) return text;
        Matcher matcher = TOKENS.matcher(text);
        StringBuilder out = new StringBuilder();
        int tail = 0;
        while (matcher.find()) {
            String replacement = label(matcher.group(), ko);
            out.append(text, tail, matcher.start()).append(replacement);
            tail = matcher.end() + appendCorrectedJosa(out, text.substring(matcher.end()), replacement);
        }
        return tail == 0 ? text : out.append(text.substring(tail)).toString();
    }

    /**
     * 치환어 뒤에 붙은 조사를 다시 고르고, 소비한 글자 수를 준다.
     * 모델은 원래 단어의 발음에 맞춰 조사를 붙였으므로("COMPLETION_RATE가") 그대로 두면 어긋난다.
     */
    static int appendCorrectedJosa(StringBuilder out, String rest, String precedingWord) {
        Matcher josa = JOSA.matcher(rest);
        if (!josa.find()) return 0;
        String corrected = correctJosa(josa.group(), precedingWord);
        out.append(corrected != null ? corrected : josa.group());
        return josa.group().length();
    }

    /**
     * 앞말의 받침에 맞는 조사를 고른다. 한글 음절과 숫자를 판정하고, 영문 제목처럼 규칙을
     * 세울 수 없는 끝글자는 원문을 그대로 둔다는 뜻으로 null을 준다.
     */
    static String correctJosa(String josa, String precedingWord) {
        if (precedingWord == null || precedingWord.isEmpty()) return null;
        char last = precedingWord.charAt(precedingWord.length() - 1);
        boolean batchim;
        boolean rieul;
        if (last >= '0' && last <= '9') {
            // 숫자로 끝나는 제목("대량 업무 9")은 읽는 소리로 판정한다. 한글 음절이 아니라고
            // 넘기면 모델이 붙인 조사가 그대로 남아 "대량 업무 9이"가 된다. 실제 문서에서 봤다.
            batchim = "013678".indexOf(last) >= 0;   // 영 일 삼 육 칠 팔
            rieul = "178".indexOf(last) >= 0;        // 일 칠 팔
        } else if (last >= 0xAC00 && last <= 0xD7A3) {
            int jongseong = (last - 0xAC00) % 28;
            batchim = jongseong != 0;
            rieul = jongseong == 8;
        } else {
            return null;
        }
        return switch (josa) {
            case "은", "는" -> batchim ? "은" : "는";
            case "이", "가" -> batchim ? "이" : "가";
            case "을", "를" -> batchim ? "을" : "를";
            case "과", "와" -> batchim ? "과" : "와";
            // 받침 ㄹ은 예외다. "정리로"가 맞고 "정리으로"는 틀리다.
            case "으로", "로" -> batchim && !rieul ? "으로" : "로";
            default -> null;
        };
    }

    private static void put(String code, String ko, String en) {
        LABELS.put(code, new String[] {ko, en});
    }
}

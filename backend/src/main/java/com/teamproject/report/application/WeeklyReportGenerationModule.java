package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.ReportContracts.AiGenerationInput;
import com.teamproject.report.application.ReportContracts.AiGenerationResult;
import com.teamproject.report.application.ReportContracts.ComparisonMetrics;
import com.teamproject.report.application.ReportContracts.EditWeeklyReportDraft;
import com.teamproject.report.application.ReportContracts.EvidenceValue;
import com.teamproject.report.application.ReportContracts.MetricsSnapshot;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.ReferenceIndex;
import com.teamproject.report.application.ReportContracts.ReportSnapshot;
import com.teamproject.report.application.ReportContracts.RevisionSummary;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import com.teamproject.report.domain.WeeklyReport;
import com.teamproject.report.domain.WeeklyReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 짧은 DB lease 트랜잭션과 장시간 외부 호출을 분리한다. 완료/실패 반영은 attempt 소유권을
 * 확인해 lease를 빼앗긴 stale worker가 새 결과를 덮어쓰지 못하게 한다.
 */
@Component
public class WeeklyReportGenerationModule {
    private static final long WEEKLY_SUCCESS_LIMIT = 3;

    private final WeeklyReportRepository reports;
    private final GroupRepository groups;
    private final MetricsSnapshotSource metricsSource;
    private final AiNarrativeGenerator narrativeGenerator;
    private final NarrativeContract contract;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration lease;

    public WeeklyReportGenerationModule(WeeklyReportRepository reports, GroupRepository groups,
            MetricsSnapshotSource metricsSource, AiNarrativeGenerator narrativeGenerator,
            NarrativeContract contract, PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${app.ai-report.generation-lease:2m}") Duration lease) {
        this.reports = reports;
        this.groups = groups;
        this.metricsSource = metricsSource;
        this.narrativeGenerator = narrativeGenerator;
        this.contract = contract;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.lease = lease;
    }

    WeeklyReportView generate(GroupMember requester, ReportPeriod period, String language) {
        Optional<WeeklyReport> existing = latest(
                requester.getGroup().getId(), period, language);
        if (existing.isPresent() && existing.get().getStatus() == WeeklyReport.Status.COMPLETED) {
            return view(existing.get(), true);
        }
        if (existing.isPresent()
                && existing.get().getStatus() != WeeklyReport.Status.FAILED
                && !existing.get().hasExpiredLease(clock.instant(), lease)) {
            throw generating();
        }

        ReportSnapshot snapshot = existing.isPresent()
                && Set.of("v3", "v4").contains(existing.get().getSchemaVersion())
                ? snapshot(existing.get())
                : metricsSource.capture(requester.getGroup().getId(), period);
        requireData(snapshot.metrics());
        WeeklyReport report = acquireInitial(requester, period, language, snapshot);
        if (report.getStatus() == WeeklyReport.Status.COMPLETED) return view(report, true);
        ReportSnapshot frozenSnapshot = snapshot(report);
        return callProvider(report, frozenSnapshot, report.getSourceReport());
    }

    WeeklyReportView regenerate(GroupMember requester, Long sourceReportId,
            long expectedEditorVersion) {
        WeeklyReport source = report(requester.getGroup().getId(), sourceReportId);
        requireV3(source);
        if (source.getPublicationStatus() == WeeklyReport.PublicationStatus.SUPERSEDED) {
            throw stateConflict("재생성할 수 있는 리포트가 아닙니다.");
        }
        if (source.getEditorVersion() != expectedEditorVersion) throw editorConflict();
        ReportSnapshot snapshot = snapshot(source);
        if (source.getStatus() == WeeklyReport.Status.FAILED
                || source.hasExpiredLease(clock.instant(), lease)) {
            WeeklyReport retried = reacquireFailedRevision(requester, source, snapshot);
            if (retried.getStatus() == WeeklyReport.Status.COMPLETED) {
                return view(retried, true);
            }
            return callProvider(retried, snapshot(retried), retried.getSourceReport());
        }
        if (source.getStatus() != WeeklyReport.Status.COMPLETED) {
            throw stateConflict("재생성할 수 있는 리포트가 아닙니다.");
        }
        WeeklyReport report = acquireRevision(requester, source, snapshot);
        return callProvider(report, snapshot, source);
    }

    WeeklyReportView edit(GroupMember requester, EditWeeklyReportDraft command) {
        WeeklyReport stored = report(requester.getGroup().getId(), command.reportId());
        requireV3(stored);
        ReportSnapshot snapshot = snapshot(stored);
        contract.validateDraft(snapshot, command.content());
        String editorialJson = contract.writeNarrative(command.content());
        try {
            transactions.executeWithoutResult(status -> {
                groups.findByIdForUpdate(requester.getGroup().getId()).orElseThrow();
                WeeklyReport value = reports.findByIdForUpdate(command.reportId()).orElseThrow();
                requireOwned(value, requester.getGroup().getId());
                if (value.getEditorVersion() != command.expectedEditorVersion()) {
                    throw editorConflict();
                }
                if (value.getPublicationStatus() != WeeklyReport.PublicationStatus.DRAFT) {
                    throw stateConflict("확정되었거나 대체된 리포트는 수정할 수 없습니다.");
                }
                value.editDraft(editorialJson, command.expectedEditorVersion(), nowLocal());
            });
        } catch (IllegalStateException exception) {
            throw stateConflict("수정할 수 있는 리포트 초안이 아닙니다.");
        }
        return view(reports.findById(command.reportId()).orElseThrow(), false);
    }

    WeeklyReportView finalizeReport(GroupMember requester, Long reportId,
            long expectedEditorVersion) {
        try {
            transactions.executeWithoutResult(status -> {
                groups.findByIdForUpdate(requester.getGroup().getId()).orElseThrow();
                WeeklyReport value = reports.findByIdForUpdate(reportId).orElseThrow();
                requireOwned(value, requester.getGroup().getId());
                requireV3(value);
                if (value.getEditorVersion() != expectedEditorVersion) throw editorConflict();
                if (value.getPublicationStatus() != WeeklyReport.PublicationStatus.DRAFT) {
                    throw stateConflict("리포트 초안만 확정할 수 있습니다.");
                }
                reports.findAllByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageOrderByRevisionDesc(
                                value.getGroup().getId(), value.getType(), value.getPeriodStart(),
                                value.getPeriodEnd(), value.getLanguage())
                        .stream()
                        .filter(other -> !other.getId().equals(value.getId()))
                        .filter(other -> other.getPublicationStatus()
                                == WeeklyReport.PublicationStatus.FINALIZED)
                        .forEach(other -> other.supersede(nowLocal()));
                value.finalizeReport(requester, expectedEditorVersion, nowLocal());
            });
        } catch (IllegalStateException exception) {
            throw stateConflict("확정할 수 있는 리포트 초안이 아닙니다.");
        }
        return view(reports.findById(reportId).orElseThrow(), false);
    }

    WeeklyReportView find(Long groupId, ReportPeriod period, String language) {
        WeeklyReport report = latest(groupId, period, language)
                .orElseThrow(() -> new ApplicationException("AI_REPORT_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "저장된 AI 주간 리포트가 없습니다."));
        return view(report, true);
    }

    WeeklyReportView findById(Long groupId, Long reportId) {
        return view(report(groupId, reportId), true);
    }

    List<RevisionSummary> revisions(Long groupId, ReportPeriod period, String language) {
        return reports
                .findAllByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageOrderByRevisionDesc(
                        groupId, WeeklyReport.Type.WEEKLY_AI,
                        period.start(), period.end(), language)
                .stream()
                .map(value -> new RevisionSummary(value.getId(), value.getRevision(),
                        value.getStatus().name(), value.getPublicationStatus().name(),
                        value.getGeneratedAt(), value.getFinalizedAt()))
                .toList();
    }

    private WeeklyReportView callProvider(WeeklyReport report, ReportSnapshot snapshot,
            WeeklyReport source) {
        Long reportId = report.getId();
        int attempt = report.getAttemptCount();
        AiGenerationResult generated = null;
        try {
            generated = narrativeGenerator.generate(
                    new AiGenerationInput(snapshot.aiContext(), report.getLanguage()));
            contract.validateGenerated(snapshot, generated.narrative());
            String narrativeJson = contract.writeNarrative(generated.narrative());
            if (completeAttempt(reportId, attempt, narrativeJson, generated, source)) {
                return view(reports.findById(reportId).orElseThrow(), false);
            }
            WeeklyReport current = reports.findById(reportId).orElseThrow();
            if (current.getStatus() == WeeklyReport.Status.COMPLETED) {
                return view(current, true);
            }
            throw generating();
        } catch (RuntimeException failure) {
            markFailed(reportId, attempt, failure, generated);
            throw failure;
        }
    }

    private WeeklyReport acquireInitial(GroupMember requester, ReportPeriod period,
            String language, ReportSnapshot snapshot) {
        try {
            return transactions.execute(status -> {
                groups.findByIdForUpdate(requester.getGroup().getId()).orElseThrow();
                Optional<WeeklyReport> locked = latest(
                        requester.getGroup().getId(), period, language);
                if (locked.isPresent()
                        && locked.get().getStatus() == WeeklyReport.Status.COMPLETED) {
                    return locked.get();
                }
                Instant now = clock.instant();
                if (locked.isPresent()
                        && locked.get().getStatus() != WeeklyReport.Status.FAILED
                        && !locked.get().hasExpiredLease(now, lease)) {
                    throw generating();
                }
                WeeklyReport value = locked.orElseGet(() -> newReport(
                        requester, period, language, 1, snapshot, null));
            ReportSnapshot attemptSnapshot = locked.isPresent()
                    && Set.of("v3", "v4").contains(value.getSchemaVersion())
                    ? snapshot(value)
                    : snapshot;
            requireGenerationBudget(requester.getGroup().getId(), period, language);
            start(value, attemptSnapshot, now);
                return reports.saveAndFlush(value);
            });
        } catch (DataIntegrityViolationException exception) {
            throw generating();
        }
    }

    private WeeklyReport reacquireFailedRevision(GroupMember requester,
            WeeklyReport requested, ReportSnapshot snapshot) {
        return transactions.execute(status -> {
            var lockedGroup =
                    groups.findByIdForUpdate(requester.getGroup().getId()).orElseThrow();
            WeeklyReport value = reports.findByIdForUpdate(requested.getId()).orElseThrow();
            requireOwned(value, requester.getGroup().getId());
            ReportPeriod reportPeriod = period(value, lockedGroup.getTimezone());
            WeeklyReport latest = latest(requester.getGroup().getId(),
                    reportPeriod, value.getLanguage()).orElseThrow();
            if (!latest.getId().equals(value.getId())) {
                throw stateConflict("이미 더 최신 리비전이 있습니다.");
            }
            if (value.getStatus() == WeeklyReport.Status.COMPLETED) return value;
            Instant now = clock.instant();
            if (value.getStatus() != WeeklyReport.Status.FAILED
                    && !value.hasExpiredLease(now, lease)) {
                throw generating();
            }
            requireGenerationBudget(requester.getGroup().getId(), reportPeriod,
                    value.getLanguage());
            start(value, snapshot, now);
            return reports.saveAndFlush(value);
        });
    }

    private WeeklyReport acquireRevision(GroupMember requester, WeeklyReport source,
            ReportSnapshot snapshot) {
        try {
            return transactions.execute(status -> {
                var lockedGroup =
                        groups.findByIdForUpdate(requester.getGroup().getId()).orElseThrow();
                ReportPeriod reportPeriod = period(source, lockedGroup.getTimezone());
                WeeklyReport latest = latest(requester.getGroup().getId(),
                        reportPeriod, source.getLanguage()).orElseThrow();
            if (!latest.getId().equals(source.getId())) {
                throw stateConflict("이미 더 최신 리비전이 있습니다.");
            }
            requireGenerationBudget(requester.getGroup().getId(), reportPeriod,
                    source.getLanguage());
            WeeklyReport value = newReport(requester, reportPeriod, source.getLanguage(),
                        source.getRevision() + 1, snapshot, source);
                start(value, snapshot, clock.instant());
                return reports.saveAndFlush(value);
            });
        } catch (DataIntegrityViolationException exception) {
            throw generating();
        }
    }

    private WeeklyReport newReport(GroupMember requester, ReportPeriod period,
            String language, int revision, ReportSnapshot snapshot, WeeklyReport source) {
        return new WeeklyReport(requester.getGroup(), requester,
                period.start(), period.end(), language, revision,
                WeeklyReport.TriggerType.USER,
                contract.writeMetrics(snapshot.metrics()),
                contract.writeAiContext(snapshot.aiContext()),
                contract.writeReferenceIndex(snapshot.references()),
                contract.writeEvidence(snapshot.evidence()),
                source, contract.promptVersion(), contract.schemaVersion(), nowLocal());
    }

    private void start(WeeklyReport value, ReportSnapshot snapshot, Instant now) {
        value.start(contract.writeMetrics(snapshot.metrics()),
                contract.writeAiContext(snapshot.aiContext()),
                contract.writeReferenceIndex(snapshot.references()),
                contract.writeEvidence(snapshot.evidence()),
                contract.promptVersion(), contract.schemaVersion(), now, nowLocal());
    }

    private boolean completeAttempt(Long reportId, int attempt, String narrativeJson,
            AiGenerationResult generated, WeeklyReport source) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            WeeklyReport value = reports.findByIdForUpdate(reportId).orElseThrow();
            if (!value.ownsGeneratingAttempt(attempt)) return false;
            value.complete(narrativeJson, generated.model(), generated.inputTokens(),
                    generated.outputTokens(), generated.totalTokens(), nowLocal());
            if (source != null) {
                reports.findByIdForUpdate(source.getId())
                        .filter(previous -> previous.getPublicationStatus()
                                == WeeklyReport.PublicationStatus.DRAFT)
                        .ifPresent(previous -> previous.supersede(nowLocal()));
            }
            return true;
        }));
    }

    private void markFailed(Long reportId, int attempt, RuntimeException failure,
            AiGenerationResult generated) {
        transactions.executeWithoutResult(status -> reports.findByIdForUpdate(reportId)
                .filter(value -> value.ownsGeneratingAttempt(attempt))
                .ifPresent(value -> value.fail(failureCode(failure),
                        generated == null ? null : generated.model(),
                        generated == null ? null : generated.inputTokens(),
                        generated == null ? null : generated.outputTokens(),
                        generated == null ? null : generated.totalTokens(),
                        nowLocal())));
    }

    private WeeklyReportView view(WeeklyReport report, boolean cached) {
        MetricsSnapshot metrics = contract.readMetrics(
                report.getSchemaVersion(), report.getMetricsJson());
        Narrative narrative = report.getEditorialJson() != null
                ? contract.readNarrative(report.getSchemaVersion(), report.getEditorialJson())
                : report.getAiSummaryJson() == null ? null
                : contract.readNarrative(report.getSchemaVersion(), report.getAiSummaryJson());
        ReportSnapshot snapshot = Set.of("v3", "v4").contains(report.getSchemaVersion())
                ? snapshot(report)
                : legacySnapshot(metrics);
        if (narrative != null && Set.of("v3", "v4").contains(report.getSchemaVersion())) {
            narrative = contract.compatibleNarrative(narrative, snapshot);
        }
        return new WeeklyReportView(report.getId(), report.getStatus().name(),
                report.getPublicationStatus().name(), report.getPeriodStart(),
                report.getPeriodEnd(), report.getLanguage(), report.getGeneratedAt(),
                report.getFinalizedAt(), report.getRevision(), report.getEditorVersion(),
                cached, metrics, snapshot.comparison(), snapshot.evidence(),
                contract.operationalView(groups.findById(report.getGroup().getId())
                        .map(group -> group.getName()).orElse("Unknown group"), snapshot),
                narrative == null ? null : contract.view(narrative, snapshot),
                report.getPublicationStatus() == WeeklyReport.PublicationStatus.DRAFT
                        ? narrative : null);
    }

    private ReportSnapshot snapshot(WeeklyReport report) {
        MetricsSnapshot metrics = contract.readMetrics(
                report.getSchemaVersion(), report.getMetricsJson());
        return contract.snapshot(metrics,
                contract.readAiContext(report.getSchemaVersion(), report.getAiContextJson()),
                contract.readReferenceIndex(
                        report.getSchemaVersion(), report.getReferenceIndexJson()),
                contract.readEvidence(report.getSchemaVersion(), report.getEvidenceJson()));
    }

    private ReportSnapshot legacySnapshot(MetricsSnapshot metrics) {
        ComparisonMetrics comparison =
                new ComparisonMetrics(false, null, null, null, null, null, null);
        Map<String, EvidenceValue> evidence = new LinkedHashMap<>();
        metrics.evidence().forEach((key, value) -> evidence.put(key,
                new EvidenceValue(key, key, Integer.toString(value), "LEGACY")));
        var context = new ReportContracts.AiReportContext(
                metrics, comparison, List.of(), List.of(), evidence.keySet());
        return new ReportSnapshot(metrics, comparison, context,
                new ReferenceIndex(List.of()), evidence);
    }

private void requireGenerationBudget(Long groupId, ReportPeriod period, String language) {
        long completed = reports.countByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndStatus(
                groupId, WeeklyReport.Type.WEEKLY_AI, period.start(), period.end(), language,
                WeeklyReport.Status.COMPLETED);
        if (completed >= WEEKLY_SUCCESS_LIMIT) {
            throw new ApplicationException("AI_REPORT_WEEKLY_LIMIT", HttpStatus.TOO_MANY_REQUESTS,
                    "AI 주간 리포트는 같은 주차에 성공한 생성·재생성을 합쳐 3회까지 가능합니다.");
        }
    }

    private Optional<WeeklyReport> latest(Long groupId, ReportPeriod period, String language) {
        return reports
                .findFirstByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageOrderByRevisionDesc(
                        groupId, WeeklyReport.Type.WEEKLY_AI,
                        period.start(), period.end(), language);
    }

    private WeeklyReport report(Long groupId, Long reportId) {
        return reports.findByIdAndGroupId(reportId, groupId)
                .orElseThrow(() -> new ApplicationException("AI_REPORT_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "저장된 AI 주간 리포트가 없습니다."));
    }

    private void requireOwned(WeeklyReport report, Long groupId) {
        if (!report.getGroup().getId().equals(groupId)) {
            throw new ApplicationException("AI_REPORT_NOT_FOUND",
                    HttpStatus.NOT_FOUND, "저장된 AI 주간 리포트가 없습니다.");
        }
    }

    private void requireV3(WeeklyReport report) {
        if (!Set.of("v3", "v4").contains(report.getSchemaVersion())) {
            throw stateConflict("기존 리포트는 조회와 인쇄만 할 수 있습니다.");
        }
    }

    private void requireData(MetricsSnapshot metrics) {
        if (metrics.totalTasks() == 0) {
            throw new ApplicationException("AI_REPORT_INSUFFICIENT_DATA",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "선택한 주간에 분석할 업무 데이터가 없습니다.");
        }
    }

    private ReportPeriod period(WeeklyReport report, String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        return new ReportPeriod(report.getPeriodStart(), report.getPeriodEnd(),
                zone,
                report.getPeriodStart().atStartOfDay(zone).toInstant(),
                report.getPeriodEnd().plusDays(1)
                        .atStartOfDay(zone).toInstant());
    }

    private String failureCode(RuntimeException failure) {
        return failure instanceof ApplicationException application
                ? application.code() : "AI_REPORT_GENERATION_FAILED";
    }

    private ApplicationException generating() {
        return new ApplicationException("AI_REPORT_GENERATING", HttpStatus.CONFLICT,
                "AI 주간 리포트를 생성하고 있습니다.");
    }

    private ApplicationException editorConflict() {
        return new ApplicationException("AI_REPORT_EDITOR_VERSION_CONFLICT",
                HttpStatus.CONFLICT, "리포트 초안이 이미 변경되었습니다.");
    }

    private ApplicationException stateConflict(String message) {
        return new ApplicationException("AI_REPORT_STATE_CONFLICT",
                HttpStatus.CONFLICT, message);
    }

    private LocalDateTime nowLocal() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

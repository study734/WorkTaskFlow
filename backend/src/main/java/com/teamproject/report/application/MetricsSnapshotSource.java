package com.teamproject.report.application;

import com.teamproject.report.application.ReportContracts.MetricsSnapshot;
import com.teamproject.report.application.ReportContracts.ReportSnapshot;

public interface MetricsSnapshotSource {
    ReportSnapshot capture(Long groupId, ReportPeriod period);

    default MetricsSnapshot snapshot(Long groupId, ReportPeriod period) {
        return capture(groupId, period).metrics();
    }
}

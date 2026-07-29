package com.teamproject.report.application;

import com.teamproject.report.application.ReportContracts.AiGenerationInput;
import com.teamproject.report.application.ReportContracts.AiGenerationResult;

public interface AiNarrativeGenerator {
    AiGenerationResult generate(AiGenerationInput input);
}

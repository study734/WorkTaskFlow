package com.teamproject.report.infrastructure.openai;

/**
 * OpenAI 주간 리포트 연동 관련 예외 클래스 모음 (M7).
 */
public class OpenAiReportExceptions {

    public static class OpenAiReportException extends RuntimeException {
        public OpenAiReportException(String message) {
            super(message);
        }

        public OpenAiReportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OpenAiReportUnavailableException extends OpenAiReportException {
        public OpenAiReportUnavailableException(String message) {
            super(message);
        }
        public OpenAiReportUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OpenAiReportTimeoutException extends OpenAiReportException {
        public OpenAiReportTimeoutException(String message) {
            super(message);
        }
        public OpenAiReportTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OpenAiReportRateLimitException extends OpenAiReportException {
        public OpenAiReportRateLimitException(String message) {
            super(message);
        }
        public OpenAiReportRateLimitException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OpenAiReportInvalidResponseException extends OpenAiReportException {
        public OpenAiReportInvalidResponseException(String message) {
            super(message);
        }
        public OpenAiReportInvalidResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

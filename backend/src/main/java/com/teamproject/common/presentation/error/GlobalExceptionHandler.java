package com.teamproject.common.presentation.error;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ApiError> application(ApplicationException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), null));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        var fields = new LinkedHashMap<String, String>();
        e.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "입력값을 확인해 주세요.", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalidRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST",
                "요청 형식과 필수 입력값을 확인해 주세요.", null));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> optimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("TASK_VERSION_CONFLICT",
                "업무가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요.", null));
    }
    /**
     * 유니크 제약 위반은 대부분 동시 요청이다. 핸들러가 없으면 계약 밖 500이 나가고
     * 프런트 ApiError 파싱도 실패한다. 원인 문구에는 SQL과 값이 들어갈 수 있어 담지 않는다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("RESOURCE_CONFLICT",
                "같은 요청이 동시에 처리되었습니다. 잠시 후 다시 시도해 주세요.", null));
    }
}

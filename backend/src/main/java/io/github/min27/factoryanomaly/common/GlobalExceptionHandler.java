package io.github.min27.factoryanomaly.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 모든 에러를 RFC 9457 ProblemDetail 형식으로 응답한다 (D-009). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UnknownEquipmentException.class)
    ProblemDetail handleUnknownEquipment(UnknownEquipmentException e) {
        // 요청 형식은 맞지만 내용(설비 코드)을 처리할 수 없으므로 422
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setTitle("Unknown equipment");
        return problem;
    }

    @ExceptionHandler(ReadingNotFoundException.class)
    ProblemDetail handleReadingNotFound(ReadingNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Reading not found");
        return problem;
    }

    @ExceptionHandler(AlertNotFoundException.class)
    ProblemDetail handleAlertNotFound(AlertNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Alert not found");
        return problem;
    }

    @ExceptionHandler(InvalidAlertTransitionException.class)
    ProblemDetail handleInvalidAlertTransition(InvalidAlertTransitionException e) {
        // 요청은 맞지만 알람의 현재 상태와 충돌하므로 409 (예: 다른 사람이 먼저 해결함)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Invalid alert transition");
        return problem;
    }

    /** 기본 응답에 어떤 필드가 왜 틀렸는지 목록을 추가한다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = e.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("입력값 검증에 실패했습니다.");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }
}

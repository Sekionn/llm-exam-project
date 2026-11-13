package dk.ek.gruppe_i.llm_project.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AiServiceException.class)
    public ProblemDetail handleAiServiceException(AiServiceException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI generation is currently unavailable. Please try again later.");
        problem.setTitle("AI unavailable");
        problem.setProperty("code", "AI_UNAVAILABLE");
        return problem;
    }
}

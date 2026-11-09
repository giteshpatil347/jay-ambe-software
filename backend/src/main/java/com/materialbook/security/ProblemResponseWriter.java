package com.materialbook.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Writes the same problem+json shape as {@code GlobalExceptionHandler} for errors raised in servlet filters. */
@Component
public class ProblemResponseWriter {
    private final ObjectMapper mapper;

    public ProblemResponseWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("message", message);
        pd.setProperty("fieldErrors", List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}

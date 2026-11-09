package com.materialbook.attachments;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** The multipart limit trips before any controller runs, so it needs its own handler to answer 422 instead of 500. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UploadExceptionAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge() {
        String message = "File is larger than the allowed upload size";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, message);
        pd.setTitle(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        pd.setProperty("code", "VALIDATION_ERROR");
        pd.setProperty("message", message);
        pd.setProperty("fieldErrors", List.of());
        return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}

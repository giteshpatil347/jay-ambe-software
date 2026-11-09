package com.materialbook.settings;

import com.materialbook.common.ApiException;
import com.materialbook.common.GlobalExceptionHandler.FieldError;
import java.util.List;

/** 422 with a single field error ({@code name} or {@code icon}) so the form can show it beside the input. */
class BrandingValidationException extends ApiException.ValidationException {
    private final List<FieldError> fieldErrors;

    BrandingValidationException(String field, String message) {
        super(message);
        this.fieldErrors = List.of(new FieldError(field, message));
    }

    @Override
    public List<FieldError> getFieldErrors() { return fieldErrors; }
}

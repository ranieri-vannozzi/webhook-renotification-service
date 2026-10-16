package it.ranierivannozzi.webhook.adapter.in.web;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 problem body.
 * <p>
 * The errors Spring MVC knows (malformed body, wrong content type, ...) are handled by the parent class with their own
 * status; an unreadable body gets a detail saying what the body must look like. Anything else is unexpected: it is
 * logged with its stack trace and the caller gets a 500 with the correlation id to quote, but no internal detail.
 */
@RestControllerAdvice
class WebExceptionHandler extends ResponseEntityExceptionHandler {

    static final String NOT_A_JSON_ARRAY = "The body must be a JSON array of items, e.g. [ { \"requestId\": \"D-001\" } ]";

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    // unexpected errors are logged with their stack trace, but the caller gets only a 500 with the correlation id to quote
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected error serving the request", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error: quote the " + CorrelationIdFilter.X_CORRELATION_ID + " response header to support");
    }

    // body is empty, e.g. an empty string or whitespace
    // valid JSON but not an array, e.g. a number or boolean or simple object
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, NOT_A_JSON_ARRAY);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

}

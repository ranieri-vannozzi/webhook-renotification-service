package it.ranierivannozzi.webhook.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 problem body.
 * <p>
 * The errors Spring MVC knows (malformed body, wrong content type, ...) are handled by the parent class with their own
 * status. Anything else is unexpected: it is logged with its stack trace and the caller gets a 500 with the
 * correlation id to quote, but no internal detail.
 */
@RestControllerAdvice
class WebExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception exception) {
		log.error("Unexpected error serving the request", exception);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Unexpected error: quote the " + CorrelationIdFilter.X_CORRELATION_ID + " response header to support");
	}

}

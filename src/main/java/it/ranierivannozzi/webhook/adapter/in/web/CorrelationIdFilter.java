package it.ranierivannozzi.webhook.adapter.in.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request a correlation id, written on each log line of the request and returned in the response.
 * <p>
 * The caller can send its own id in the {@value #X_CORRELATION_ID} header, so the operator tool and this service log
 * the same value; otherwise a new one is generated. Only short ids of letters, digits and dashes are accepted, so a
 * header cannot forge log lines.
 */
@Component
class CorrelationIdFilter extends OncePerRequestFilter {

	static final String MDC_KEY = "correlationId";
	static final String X_CORRELATION_ID = "X-Correlation-Id";

	private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String correlationId = correlationIdOf(request);
		MDC.put(MDC_KEY, correlationId);
		response.setHeader(X_CORRELATION_ID, correlationId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	private static String correlationIdOf(HttpServletRequest request) {
		@Nullable String correlationId = request.getHeader(X_CORRELATION_ID);
		if (correlationId != null && VALID_ID.matcher(correlationId).matches()) {
			return correlationId;
		}
		return UUID.randomUUID().toString();
	}

}

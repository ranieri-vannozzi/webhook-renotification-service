package it.ranierivannozzi.webhook.adapter.in.web;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import it.ranierivannozzi.webhook.application.port.in.FailedStep;

/**
 * One element of the response body of {@code POST /v1/webhook/oneshot}, as defined by the HTTP contract.
 * <p>
 * {@code requestId} is always written, as {@code null} for an element that did not have one; the other optional
 * fields are written only when present.
 */
@JsonInclude(Include.NON_NULL)
public record OneShotItemResponse(
        @JsonInclude(Include.ALWAYS) @Nullable String requestId,
        @Nullable String containerStatus,
        Outcome outcome,
        @Nullable FailedStep failedStep,
        @Nullable String error) {

    public enum Outcome {
        QUEUED,
        FAILED
    }

}

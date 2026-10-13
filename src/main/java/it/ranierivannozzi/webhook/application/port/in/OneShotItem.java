package it.ranierivannozzi.webhook.application.port.in;

import org.jspecify.annotations.Nullable;

/**
 * One element of a one-shot request, as sent by the caller.
 * <p>
 * Both fields are kept raw: a missing {@code requestId} or an unknown {@code containerStatus} fails only this item,
 * and the result must echo the values exactly as received.
 */
public record OneShotItem(@Nullable String requestId, @Nullable String containerStatus) {
}

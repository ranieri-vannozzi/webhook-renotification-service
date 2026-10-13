package it.ranierivannozzi.webhook.adapter.in.web;

import org.jspecify.annotations.Nullable;

/**
 * One element of the request body of {@code POST /v1/webhook/oneshot}, as defined by the HTTP contract.
 * <p>
 * {@code requestId} is mandatory in the contract but nullable here: an element without it fails on its own instead
 * of rejecting the whole request.
 */
public record OneShotItemRequest(@Nullable String requestId, @Nullable String containerStatus) {
}

package it.ranierivannozzi.webhook.domain.model;

import java.net.URI;

/**
 * The webhook endpoint of a customer. Credentials are not part of the model: the flow that performs the call
 * resolves them from the auth type.
 */
public record WebhookConfiguration(String customerId, boolean enabled, URI url, WebhookAuthType authType) {
}

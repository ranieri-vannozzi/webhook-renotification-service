package it.ranierivannozzi.webhook.domain.model;

/**
 * How the customer endpoint authenticates the webhook call.
 */
public enum WebhookAuthType {
	BASIC,
	OAUTH2,
	HEADER
}

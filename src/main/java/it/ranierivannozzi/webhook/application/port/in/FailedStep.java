package it.ranierivannozzi.webhook.application.port.in;

/**
 * The step of the one-shot flow where an item failed, as listed by the HTTP contract.
 */
public enum FailedStep {
	DOCUMENT_LOOKUP,
	CONTAINER_LOOKUP,
	CONFIG_RESOLUTION,
	CHANNEL_SEND
}

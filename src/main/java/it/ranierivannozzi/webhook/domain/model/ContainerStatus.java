package it.ranierivannozzi.webhook.domain.model;

/**
 * Processing state of a container.
 */
public enum ContainerStatus {
	SENT,
	DELIVERED,
	ACCEPTED,
	REJECTED
}

package it.ranierivannozzi.webhook.domain.model;

/**
 * Processing state of a container (payload).
 */
public enum ContainerStatus {
	SENT,
	DELIVERED,
	ACCEPTED,
	REJECTED
}

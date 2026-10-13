package it.ranierivannozzi.webhook.domain.model;

import java.net.URI;

/**
 * The message put on the internal queue. It carries everything the delivery flow needs to call the customer
 * without reading the source systems again.
 */
public record NotificationMessage(
		String requestId,
		String customerId,
		String containerId,
		DocumentVersion document,
		ContainerStatus containerStatus,
		URI webhookUrl,
		WebhookAuthType authType) {
}

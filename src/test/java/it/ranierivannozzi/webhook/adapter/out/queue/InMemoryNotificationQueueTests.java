package it.ranierivannozzi.webhook.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.model.NotificationMessage;
import it.ranierivannozzi.webhook.domain.model.WebhookAuthType;

class InMemoryNotificationQueueTests {

	@Test
	void keepsTheMessagesInArrivalOrder() {
		InMemoryNotificationQueue queue = new InMemoryNotificationQueue();
		NotificationMessage first = message("D-001");
		NotificationMessage second = message("D-004");

		queue.enqueue(first);
		queue.enqueue(second);

		assertThat(queue.messages()).containsExactly(first, second);
	}

	private static NotificationMessage message(String requestId) {
		DocumentVersion version = new DocumentVersion(1, Instant.EPOCH, BigDecimal.ONE, Currency.getInstance("EUR"));
		return new NotificationMessage(requestId, "CUST-A", "C-1", version, ContainerStatus.SENT,
				URI.create("https://hooks.example/a"), WebhookAuthType.BASIC);
	}

}

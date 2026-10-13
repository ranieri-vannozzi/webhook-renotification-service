package it.ranierivannozzi.webhook.adapter.out.queue;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.ranierivannozzi.webhook.application.port.out.NotificationQueue;
import it.ranierivannozzi.webhook.domain.model.NotificationMessage;

/**
 * Stands in for the Redis-backed internal queue: keeps the messages in memory, in arrival order, and is safe to call
 * from several threads. Nothing consumes them, since the delivery flow is out of scope.
 */
public class InMemoryNotificationQueue implements NotificationQueue {

	private final Queue<NotificationMessage> messages = new ConcurrentLinkedQueue<>();
	private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationQueue.class);

	@Override
	public void enqueue(NotificationMessage message) {
		messages.add(message);
		log.info("Enqueued notification for document {} version {} in status {} to {}", message.requestId(),
				message.document().version(), message.containerStatus(), message.webhookUrl());
	}

	/**
	 * @return the messages enqueued so far, oldest first
	 */
	public List<NotificationMessage> messages() {
		return List.copyOf(messages);
	}

}

package it.ranierivannozzi.webhook.adapter.out.queue;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.ranierivannozzi.webhook.application.port.out.NotificationQueue;
import it.ranierivannozzi.webhook.domain.model.NotificationMessage;

/**
 * Stands in for the Redis-backed internal queue, with the structures a reliable queue keeps there: the messages ready
 * for delivery, the ones being delivered, the retries scheduled for later and the dead letters.
 * <p>
 * The delivery flow, out of scope here, {@linkplain #poll() takes} a message and then either
 * {@linkplain #acknowledge(String) acknowledges} it or asks to {@linkplain #retry(String) retry} it. A retry waits an
 * exponential backoff ({@code initialBackoff}, then twice as long at each attempt); after {@code maxAttempts}
 * deliveries the message goes to the dead letters. All methods are safe to call from several threads.
 */
@Component
public class InMemoryNotificationQueue implements NotificationQueue {

	private final int maxAttempts;
	private final InstantSource clock;
	private final Duration initialBackoff;
	private final Deque<QueuedNotification> ready = new ArrayDeque<>();
	private final List<QueuedNotification> deadLetters = new ArrayList<>();
	private final Map<String, QueuedNotification> inFlight = new HashMap<>();
	private final PriorityQueue<Retry> retries = new PriorityQueue<>(Comparator.comparing(Retry::dueAt));
	private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationQueue.class);

	/**
	 * @param clock tells when a scheduled retry is due
	 * @param maxAttempts how many deliveries a message gets before going to the dead letters
	 * @param initialBackoff how long the first retry waits, doubled at each further one
	 */
	public InMemoryNotificationQueue(InstantSource clock, @Value("${oneshot.queue-max-attempts}") int maxAttempts,
			@Value("${oneshot.queue-initial-backoff}") Duration initialBackoff) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
		}
		if (!initialBackoff.isPositive()) {
			throw new IllegalArgumentException("initialBackoff must be positive, was " + initialBackoff);
		}
		this.clock = clock;
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
	}

	@Override
	public synchronized void enqueue(NotificationMessage message) {
		QueuedNotification queued = new QueuedNotification(UUID.randomUUID().toString(), 1, message);
		ready.addLast(queued);
		log.info("Enqueued notification {} for document {} version {} in status {} to {}", queued.id(),
				message.requestId(), message.document().version(), message.containerStatus(), message.webhookUrl());
	}

	/**
	 * Hands the oldest message ready for delivery to the delivery flow, which must then acknowledge it or ask to
	 * retry it.
	 *
	 * @return the message to deliver, empty when none is ready
	 */
	public synchronized Optional<QueuedNotification> poll() {
		moveDueRetriesToReady();
		QueuedNotification next = ready.pollFirst();
		if (next == null) {
			return Optional.empty();
		}
		inFlight.put(next.id(), next);
		return Optional.of(next);
	}

	/**
	 * The customer received the message: it leaves the queue.
	 *
	 * @throws IllegalArgumentException if no message with this id is being delivered
	 */
	public synchronized void acknowledge(String id) {
		takeInFlight(id);
	}

	/**
	 * The delivery failed: the message is delivered again after the backoff of its attempt, or goes to the dead
	 * letters once it has used all of its attempts.
	 *
	 * @throws IllegalArgumentException if no message with this id is being delivered
	 */
	public synchronized void retry(String id) {
		QueuedNotification failed = takeInFlight(id);
		if (failed.attempt() >= maxAttempts) {
			deadLetters.add(failed);
			log.error("Notification {} for document {} failed {} times: moved to the dead letters", id,
					failed.message().requestId(), failed.attempt());
			return;
		}
		Duration backoff = initialBackoff.multipliedBy(1L << (failed.attempt() - 1));
		retries.add(new Retry(clock.instant().plus(backoff),
				new QueuedNotification(id, failed.attempt() + 1, failed.message())));
		log.warn("Notification {} for document {} failed attempt {}: retrying in {}", id,
				failed.message().requestId(), failed.attempt(), backoff);
	}

	/**
	 * @return the messages ready for delivery, oldest first
	 */
	public synchronized List<NotificationMessage> messages() {
		moveDueRetriesToReady();
		return ready.stream().map(QueuedNotification::message).toList();
	}

	/**
	 * @return the messages that used all of their attempts, oldest first
	 */
	public synchronized List<QueuedNotification> deadLetters() {
		return List.copyOf(deadLetters);
	}

	private QueuedNotification takeInFlight(String id) {
		QueuedNotification queued = inFlight.remove(id);
		if (queued == null) {
			throw new IllegalArgumentException("No notification " + id + " is being delivered");
		}
		return queued;
	}

	private void moveDueRetriesToReady() {
		Instant now = clock.instant();
		while (!retries.isEmpty() && !retries.peek().dueAt().isAfter(now)) {
			ready.addLast(retries.poll().notification());
		}
	}

	private record Retry(Instant dueAt, QueuedNotification notification) {
	}

}

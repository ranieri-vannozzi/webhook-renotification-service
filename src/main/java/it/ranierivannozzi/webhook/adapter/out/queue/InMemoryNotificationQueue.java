package it.ranierivannozzi.webhook.adapter.out.queue;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
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
 * Stands in for the Redis-backed internal queue: an in-memory queue with retries and dead letters. Unlike the real
 * one, it loses its messages on restart.
 * <p>
 * The delivery flow, out of scope here, {@linkplain #poll() takes} a message and calls the customer; when the call
 * fails, it asks to {@linkplain #retry(QueuedNotification) retry} the message. A retry waits an exponential backoff
 * ({@code initialBackoff}, then twice as long at each attempt); after {@code maxAttempts} deliveries the message goes
 * to the dead letters. All methods are safe to call from several threads.
 * <p>
 * A message moves in these steps:
 * <ul>
 * <li>step 1, {@link #enqueue}: a new message goes to {@code ready};</li>
 * <li>step 2, {@link #poll()}: the oldest message leaves {@code ready} and the queue, handed to the delivery flow;</li>
 * <li>step 3, {@link #retry(QueuedNotification)}: the delivery failed, the message goes to {@code retries}, or to
 * {@code deadLetters} after its last attempt;</li>
 * <li>step 4, at the next {@link #poll()}: once its backoff is over, it moves from {@code retries} back to
 * {@code ready}.</li>
 * </ul>
 * A delivered message needs no further call: it already left the queue at step 2.
 */
@Component
public class InMemoryNotificationQueue implements NotificationQueue {

	private final int maxAttempts;
	private final Duration initialBackoff;

	private final InstantSource clock;

	private final Deque<QueuedNotification> ready = new ArrayDeque<>();
	private final List<QueuedNotification> deadLetters = new ArrayList<>();
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

	/**
	 * Step 1. The message gets a new id and its first attempt, and waits in {@code ready}.
	 */
	@Override
	public synchronized void enqueue(NotificationMessage message) {
		QueuedNotification queued = new QueuedNotification(UUID.randomUUID().toString(), 1, message);
		ready.addLast(queued);
		log.info("Enqueued notification {} for document {} version {} in status {} to {}", queued.id(),
				message.requestId(), message.document().version(), message.containerStatus(), message.webhookUrl());
	}

	/**
	 * NOW ONLY FOR TEST - Step 2, after step 4 for the retries that are due.
	 * Move from retries the element with backoff over into ready queue.
	 *
	 * @return the message to deliver, empty when none is ready
	 */
	public synchronized Optional<QueuedNotification> poll() {
		moveDueRetriesToReady();
		return Optional.ofNullable(ready.pollFirst());
	}

	/**
	 * NOW ONLY FOR TEST - Step 3. The delivery failed: the message is delivered again after the backoff of its attempt, or goes to the dead
	 * letters once it has used all of its attempts.
	 *
	 * @param failed the message as {@link #poll()} returned it
	 */
	public synchronized void retry(QueuedNotification failed) {

		if (failed.attempt() >= maxAttempts) {
			deadLetters.add(failed);
			log.error("Notification {} for document {} failed {} times: moved to the dead letters", failed.id(),
					failed.message().requestId(), failed.attempt());
			return;
		}

		Duration backoff = initialBackoff.multipliedBy(1L << (failed.attempt() - 1));

		retries.add(new Retry(clock.instant().plus(backoff),
				new QueuedNotification(failed.id(), failed.attempt() + 1, failed.message())));

		log.warn("Notification {} for document {} failed attempt {}: retrying in {}", failed.id(),
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
	 * NOW ONLY FOR TEST
	 * @return the messages that used all of their attempts, oldest first
	 */
	public synchronized List<QueuedNotification> deadLetters() {
		return List.copyOf(deadLetters);
	}

	/**
	 * Step 4. The retries whose backoff is over go back to {@code ready}, after the messages already there.
	 */
	private void moveDueRetriesToReady() {
		Instant now = clock.instant();
		while (!retries.isEmpty() && !retries.peek().dueAt().isAfter(now)) {
			ready.addLast(retries.poll().notification());
		}
	}

	private record Retry(Instant dueAt, QueuedNotification notification) {
	}

}

package it.ranierivannozzi.webhook.adapter.out.queue;

import it.ranierivannozzi.webhook.domain.model.NotificationMessage;

/**
 * A message as the delivery flow takes it from the queue.
 *
 * @param id assigned when the message is enqueued and kept across attempts, so the customer can recognise a
 * redelivery
 * @param attempt 1 on the first delivery, increased at each retry
 * @param message what to deliver
 */
public record QueuedNotification(String id, int attempt, NotificationMessage message) {
}

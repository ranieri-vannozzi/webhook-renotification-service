package it.ranierivannozzi.webhook.application.port.out;

import it.ranierivannozzi.webhook.domain.model.NotificationMessage;

/**
 * The internal queue the delivery flow reads from. Implementations throw an unchecked exception when the message
 * could not be accepted.
 */
public interface NotificationQueuePort {

	void enqueue(NotificationMessage message);

}

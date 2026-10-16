package it.ranierivannozzi.webhook.application.port.in;

/**
 * The step of the one-shot flow where an item failed, as listed by the HTTP contract.
 */
public enum FailedStep {
    DOCUMENT_LOOKUP, // The document was not found in the database.
    CONTAINER_LOOKUP, // The container was not found in the database.
    CONFIG_RESOLUTION, // The webhook configuration was not found for the customer.
    CHANNEL_SEND // The message could not be sent to the notification channel.
}

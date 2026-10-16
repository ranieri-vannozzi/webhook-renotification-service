package it.ranierivannozzi.webhook.application.port.in;

/**
 * Outcome of one item of a one-shot request.
 */
public sealed interface ItemResult {

    OneShotItem originalItem();

    /**
     * The notification is on the internal queue; delivery to the customer happens later, in another flow.
     */
    record Queued(OneShotItem originalItem) implements ItemResult {
    }

    /**
     * @param error readable by an operator
     */
    record Failed(OneShotItem originalItem, FailedStep step, String error) implements ItemResult {
    }

}

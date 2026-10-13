package it.ranierivannozzi.webhook.application.port.in;

import java.util.List;

/**
 * Re-sends the webhook notification for a list of documents on an operator's request.
 */
public interface SendOneShotUseCase {

	/**
	 * Processes every item independently: a failing item does not stop the others.
	 *
	 * @return one result per item, in the same order
	 */
	List<ItemResult> send(List<OneShotItem> items);

}

package it.ranierivannozzi.webhook.adapter.in.web;

import org.jspecify.annotations.Nullable;

import it.ranierivannozzi.webhook.adapter.in.web.OneShotItemResponse.Outcome;
import it.ranierivannozzi.webhook.application.port.in.ItemResult;
import it.ranierivannozzi.webhook.application.port.in.OneShotItem;

/**
 * Converts between the HTTP contract and the use case. Values are echoed exactly as received.
 */
final class OneShotWebMapper {

	private OneShotWebMapper() {
	}

	/**
	 * A {@code null} element of the request list becomes an item without {@code requestId}, which fails on its own.
	 */
	static OneShotItem toItem(@Nullable OneShotItemRequest request) {
		return request == null
				? new OneShotItem(null, null)
				: new OneShotItem(request.requestId(), request.containerStatus());
	}

	static OneShotItemResponse toResponse(ItemResult result) {
		OneShotItem originalItem = result.originalItem();
		return switch (result) {
			case ItemResult.Queued queued -> new OneShotItemResponse(originalItem.requestId(),
					originalItem.containerStatus(), Outcome.QUEUED, null, null);
			case ItemResult.Failed failed -> new OneShotItemResponse(originalItem.requestId(),
					originalItem.containerStatus(), Outcome.FAILED, failed.step(), failed.error());
		};
	}

}

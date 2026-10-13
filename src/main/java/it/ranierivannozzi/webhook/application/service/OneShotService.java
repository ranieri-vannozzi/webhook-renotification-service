package it.ranierivannozzi.webhook.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.ranierivannozzi.webhook.application.port.in.FailedStep;
import it.ranierivannozzi.webhook.application.port.in.ItemResult;
import it.ranierivannozzi.webhook.application.port.in.OneShotItem;
import it.ranierivannozzi.webhook.application.port.in.SendOneShotUseCase;
import it.ranierivannozzi.webhook.application.port.out.ContainerRepository;
import it.ranierivannozzi.webhook.application.port.out.DocumentRepository;
import it.ranierivannozzi.webhook.application.port.out.NotificationQueue;
import it.ranierivannozzi.webhook.application.port.out.WebhookConfigurationRepository;
import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.NotificationMessage;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;
import it.ranierivannozzi.webhook.domain.resolution.DocumentStateResolver;
import it.ranierivannozzi.webhook.domain.resolution.Resolution;
import it.ranierivannozzi.webhook.domain.resolution.Resolution.Resolved;

/**
 * Sends one-shot notifications: for each item, looks up the document and its containers, resolves the state to
 * notify, resolves the customer webhook configuration and enqueues the message.
 * <p>
 * Items run in parallel on virtual threads, each through the three steps in order. A semaphore shared by all
 * requests caps how many items are in flight at once, to protect the document system and the database. Duplicate
 * items (same {@code requestId} and {@code containerStatus}, as sent) are processed once and share the result.
 */
public class OneShotService implements SendOneShotUseCase {

	private final Semaphore permits;
	private final NotificationQueue queue;
	private final DocumentRepository documents;
	private final ContainerRepository containers;
	private final WebhookConfigurationRepository configurations;
	private static final Logger log = LoggerFactory.getLogger(OneShotService.class);

	/**
	 * @param maxConcurrentItems how many items may be processed at the same time across all requests
	 */
	public OneShotService(DocumentRepository documents, ContainerRepository containers,
			WebhookConfigurationRepository configurations, NotificationQueue queue, int maxConcurrentItems) {
		if (maxConcurrentItems < 1) {
			throw new IllegalArgumentException("maxConcurrentItems must be at least 1, was " + maxConcurrentItems);
		}
		this.documents = documents;
		this.containers = containers;
		this.configurations = configurations;
		this.queue = queue;
		this.permits = new Semaphore(maxConcurrentItems, true);
	}

	@Override
	public List<ItemResult> send(List<OneShotItem> items) {
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Map<OneShotItem, Future<ItemResult>> distinct = new LinkedHashMap<>();
			List<Future<ItemResult>> perItem = new ArrayList<>(items.size());
			for (OneShotItem item : items) {
				perItem.add(distinct.computeIfAbsent(item, key -> executor.submit(() -> processWithPermit(key))));
			}
			return perItem.stream().map(OneShotService::await).toList();
		}
	}

	private ItemResult processWithPermit(OneShotItem item) throws InterruptedException {
		permits.acquire();
		try {
			ItemResult result = process(item);
			logResult(result);
			return result;
		}
		finally {
			permits.release();
		}
	}

	private ItemResult process(OneShotItem item) {
		try {
			enqueue(item);
			return new ItemResult.Queued(item);
		}
		catch (ItemFailure failure) {
			return new ItemResult.Failed(item, failure.step, failure.error);
		}
	}

	/**
	 * Runs the three steps for one item, throwing {@link ItemFailure} at the first one that fails.
	 */
	private void enqueue(OneShotItem item) {
		String requestId = item.requestId();
		if (requestId == null || requestId.isBlank()) {
			throw new ItemFailure(FailedStep.DOCUMENT_LOOKUP, "requestId is missing");
		}
		ContainerStatus requestedStatus = parseStatus(item.containerStatus());

		// Step 1: document and container
		Document document = call(FailedStep.DOCUMENT_LOOKUP, "document", () -> documents.findByRequestId(requestId))
				.orElseThrow(() -> new ItemFailure(FailedStep.DOCUMENT_LOOKUP, "document not found"));

		List<Container> documentContainers = call(FailedStep.CONTAINER_LOOKUP, "container",
				() -> containers.findByDocumentRequestId(requestId));

		Resolved resolved = switch (DocumentStateResolver.resolve(document, documentContainers, requestedStatus)) {
			case Resolution.Failed failed -> throw new ItemFailure(stepOf(failed), failed.message());
			case Resolved ok -> ok;
		};

		logResolution(requestId, resolved);

		// Step 2: webhook configuration of the document owner
		WebhookConfiguration configuration = resolveConfiguration(document.customerId());

		// Step 3: internal queue
		NotificationMessage message = new NotificationMessage(
				requestId,
				document.customerId(),
				resolved.container().containerId(),
				resolved.version(),
				resolved.status(),
				configuration.url(),
				configuration.authType());
		call(FailedStep.CHANNEL_SEND, "notification queue", () -> queue.enqueue(message));
	}

	private @Nullable ContainerStatus parseStatus(@Nullable String containerStatus) {
		if (containerStatus == null) {
			return null;
		}
		try {
			return ContainerStatus.valueOf(containerStatus);
		}
		catch (IllegalArgumentException e) {
			throw new ItemFailure(FailedStep.CONTAINER_LOOKUP, "unknown container status '" + containerStatus + "'");
		}
	}

	private WebhookConfiguration resolveConfiguration(String customerId) {
		Optional<WebhookConfiguration> configuration = call(FailedStep.CONFIG_RESOLUTION, "webhook configuration",
				() -> configurations.findByCustomerId(customerId));
		if (configuration.isEmpty()) {
			throw new ItemFailure(FailedStep.CONFIG_RESOLUTION, "no webhook configuration for customer " + customerId);
		}
		if (!configuration.get().enabled()) {
			throw new ItemFailure(FailedStep.CONFIG_RESOLUTION, "webhook disabled for customer " + customerId);
		}
		return configuration.get();
	}

	/**
	 * Calls an outbound port, turning a technical failure into a failure of the given step. The cause is logged;
	 * the operator only gets a short message.
	 */
	private <T> T call(FailedStep step, String source, Supplier<T> port) {
		try {
			return port.get();
		}
		catch (RuntimeException e) {
			log.warn("{} failed calling the {} source", step, source, e);
			throw new ItemFailure(step, source + " unavailable");
		}
	}

	private void call(FailedStep step, String source, Runnable port) {
		call(step, source, () -> {
			port.run();
			return Boolean.TRUE;
		});
	}

	private static FailedStep stepOf(Resolution.Failed failed) {
		return switch (failed.reason()) {
			case DOCUMENT_HAS_NO_VERSIONS, DOCUMENT_VERSION_NOT_FOUND -> FailedStep.DOCUMENT_LOOKUP;
			case NO_CONTAINER, CONTAINER_STATUS_NOT_FOUND -> FailedStep.CONTAINER_LOOKUP;
		};
	}

	private static void logResolution(String requestId, Resolved resolved) {
		if (!resolved.discardedContainers().isEmpty()) {
			log.warn("Document {} has {} containers: using {}, discarding {}", requestId,
					resolved.discardedContainers().size() + 1, resolved.container().containerId(),
					resolved.discardedContainers().stream().map(Container::containerId).toList());
		}
		if (resolved.currentContainerFallback()) {
			log.info("No snapshot of container {} refers to version {} of document {}: using its current status {}",
					resolved.container().containerId(), resolved.version().version(), requestId, resolved.status());
		}
	}

	private static void logResult(ItemResult result) {
		switch (result) {
			case ItemResult.Queued queued -> log.info("One-shot item {} queued", queued.item());
			case ItemResult.Failed failed -> log.info("One-shot item {} failed at {}: {}", failed.item(),
					failed.step(), failed.error());
		}
	}

	private static ItemResult await(Future<ItemResult> result) {
		try {
			return result.get();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for one-shot items", e);
		}
		catch (ExecutionException e) {
			throw new IllegalStateException("One-shot item processing failed unexpectedly", e.getCause());
		}
	}

	/**
	 * Stops the processing of one item at the step that failed. Never leaves the service.
	 */
	private static final class ItemFailure extends RuntimeException {

		private final String error;
		private final FailedStep step;

		ItemFailure(FailedStep step, String error) {
			super(error, null, false, false);
			this.step = step;
			this.error = error;
		}

	}

}

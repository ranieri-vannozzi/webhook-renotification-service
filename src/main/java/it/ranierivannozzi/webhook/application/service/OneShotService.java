package it.ranierivannozzi.webhook.application.service;

import java.time.Duration;
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
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.ranierivannozzi.webhook.application.port.in.FailedStep;
import it.ranierivannozzi.webhook.application.port.in.ItemResult;
import it.ranierivannozzi.webhook.application.port.in.OneShotItem;
import it.ranierivannozzi.webhook.application.port.in.OneShotServicePort;
import it.ranierivannozzi.webhook.application.port.out.ContainerRepositoryPort;
import it.ranierivannozzi.webhook.application.port.out.DocumentRepositoryPort;
import it.ranierivannozzi.webhook.application.port.out.NotificationQueuePort;
import it.ranierivannozzi.webhook.application.port.out.WebhookConfigurationRepositoryPort;
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
 * <p>
 * The logging context of the caller (for example the correlation id of the HTTP request) is copied to the thread of
 * each item, so every log line of a request can be found together.
 */
@Service
public class OneShotService implements OneShotServicePort {

	private final Semaphore permits;
	private final NotificationQueuePort notificationQueuePort;
	private final DocumentRepositoryPort documentRepositoryPort;
	private final ContainerRepositoryPort containerRepositoryPort;
	private static final Logger log = LoggerFactory.getLogger(OneShotService.class);
	private final WebhookConfigurationRepositoryPort webhookConfigurationRepositoryPort;

	/**
	 * @param maxConcurrentItems how many items may be processed at the same time across all requests
	 */
	public OneShotService(DocumentRepositoryPort documentRepositoryPort,
			ContainerRepositoryPort containerRepositoryPort,
			WebhookConfigurationRepositoryPort webhookConfigurationRepositoryPort,
			NotificationQueuePort notificationQueuePort,
			@Value("${oneshot.max-concurrent-items}") int maxConcurrentItems) {
		if (maxConcurrentItems < 1) {
			throw new IllegalArgumentException("maxConcurrentItems must be at least 1, was " + maxConcurrentItems);
		}
		this.documentRepositoryPort = documentRepositoryPort;
		this.containerRepositoryPort = containerRepositoryPort;
		this.webhookConfigurationRepositoryPort = webhookConfigurationRepositoryPort;
		this.notificationQueuePort = notificationQueuePort;
		this.permits = new Semaphore(maxConcurrentItems, true);
	}

	@Override
	public List<ItemResult> send(List<OneShotItem> items) {
		long start = System.nanoTime();
		@Nullable Map<String, String> logContext = MDC.getCopyOfContextMap();
		List<ItemResult> results;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Map<OneShotItem, Future<ItemResult>> distinct = new LinkedHashMap<>();
			List<Future<ItemResult>> perItem = new ArrayList<>(items.size());
			for (OneShotItem item : items) {
				perItem.add(distinct.computeIfAbsent(item,
						key -> executor.submit(() -> processWithPermit(key, logContext))));
			}
			results = perItem.stream().map(OneShotService::await).toList();
		}
		logSummary(results, Duration.ofNanos(System.nanoTime() - start));
		return results;
	}

	private ItemResult processWithPermit(OneShotItem item, @Nullable Map<String, String> logContext)
			throws InterruptedException {
		if (logContext != null) {
			MDC.setContextMap(logContext);
		}
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
		Document document = call(FailedStep.DOCUMENT_LOOKUP, "document",
				() -> documentRepositoryPort.findByRequestId(requestId))
				.orElseThrow(() -> new ItemFailure(FailedStep.DOCUMENT_LOOKUP, "document not found"));

		List<Container> documentContainers = call(FailedStep.CONTAINER_LOOKUP, "container",
				() -> containerRepositoryPort.findByDocumentRequestId(requestId));

		Resolved resolved = switch (resolve(document, documentContainers, requestedStatus)) {
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
		call(FailedStep.CHANNEL_SEND, "notification queue", () -> notificationQueuePort.enqueue(message));
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
				() -> webhookConfigurationRepositoryPort.findByCustomerId(customerId));
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

	/**
	 * Applies the domain rules of step 1. They only read the data already loaded, so an exception here is a bug:
	 * it fails this item, not the whole request, since the other items may already be on the queue.
	 */
	private static Resolution resolve(Document document, List<Container> documentContainers,
			@Nullable ContainerStatus requestedStatus) {
		try {
			return DocumentStateResolver.resolve(document, documentContainers, requestedStatus);
		}
		catch (RuntimeException e) {
			log.error("Could not resolve the state of document {}", document.requestId(), e);
			throw new ItemFailure(FailedStep.CONTAINER_LOOKUP, "could not resolve the state to notify");
		}
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
			case ItemResult.Queued queued -> log.info("One-shot item {} queued", queued.originalItem());
			case ItemResult.Failed failed -> log.info("One-shot item {} failed at {}: {}", failed.originalItem(),
					failed.step(), failed.error());
		}
	}

	private static void logSummary(List<ItemResult> results, Duration elapsed) {
		long queued = results.stream().filter(result -> result instanceof ItemResult.Queued).count();
		log.info("One-shot request of {} items done in {} ms: {} queued, {} failed", results.size(),
				elapsed.toMillis(), queued, results.size() - queued);
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

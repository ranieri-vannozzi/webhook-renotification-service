package it.ranierivannozzi.webhook.application.service;

import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.ACCEPTED;
import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.DELIVERED;
import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import it.ranierivannozzi.webhook.application.port.in.FailedStep;
import it.ranierivannozzi.webhook.application.port.in.ItemResult;
import it.ranierivannozzi.webhook.application.port.in.OneShotItem;
import it.ranierivannozzi.webhook.application.port.out.ContainerRepositoryPort;
import it.ranierivannozzi.webhook.application.port.out.DocumentRepositoryPort;
import it.ranierivannozzi.webhook.application.port.out.NotificationQueuePort;
import it.ranierivannozzi.webhook.application.port.out.WebhookConfigurationRepositoryPort;
import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerSnapshot;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.model.NotificationMessage;
import it.ranierivannozzi.webhook.domain.model.WebhookAuthType;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

/**
 * The outbound ports are replaced by lambdas over maps holding a subset of dataset.json.
 */
class OneShotServiceUnitTests {

    private static final URI CUST_A_URL = URI.create("https://hooks.cliente-a.example/einvoice");

    // The maps holding the dataset. The keys are the requestId for documents and containers, and the customerId for configurations.
    private final Map<String, Document> documentsByIdMap = new HashMap<>();
    private final Map<String, List<Container>> containersByDocumentMap = new HashMap<>();
    private final Map<String, WebhookConfiguration> configurationsByCustomerMap = new HashMap<>();

    // The list of queued messages, to check that the service sends the expected messages to the queue.
    // It is thread-safe because the items are processed in parallel.
    private final List<NotificationMessage> queued = new CopyOnWriteArrayList<>();

    // The number of lookups per requestId, filled by the documents port below. Thread-safe for the same reason.
    private final Map<String, AtomicInteger> documentLookups = new ConcurrentHashMap<>();

    // Documents port: it counts the lookups per requestId, to check that duplicate items are looked up only once.
    private DocumentRepositoryPort documentRepositoryPort = id -> {
        documentLookups.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
        return Optional.ofNullable(documentsByIdMap.get(id));
    };

    // Containers port.
    private ContainerRepositoryPort containerRepositoryPort = id -> containersByDocumentMap.getOrDefault(id, List.of());

    // Webhook configurations port.
    private WebhookConfigurationRepositoryPort webhookConfigurationRepositoryPort = id -> Optional
            .ofNullable(configurationsByCustomerMap.get(id));

    // Notification queue port: it stores the queued messages in a list.
    private NotificationQueuePort notificationQueuePort = queued::add;

    @BeforeEach
    void dataset() {
        // Add the documents and their containers. The versions and the snapshots simulate different states.
        add(document("D-001", "CUST-A", 1, 2, 3), container("C-001", "D-001",
                snapshot(SENT, 1, "2026-09-01T09:00:10Z"),
                snapshot(DELIVERED, 2, "2026-09-01T09:05:10Z"),
                snapshot(ACCEPTED, 3, "2026-09-02T14:30:10Z")));

        add(document("D-004", "CUST-B", 1, 2), container("C-004", "D-004",
                snapshot(SENT, 1, "2026-09-07T07:30:10Z"),
                snapshot(DELIVERED, 2, "2026-09-07T07:31:10Z")));

        add(document("D-005", "CUST-C", 1), container("C-005", "D-005",
                snapshot(SENT, 1, "2026-09-08T12:00:10Z")));

        add(document("D-006", "CUST-D", 1, 2), container("C-006", "D-006",
                snapshot(SENT, 1, "2026-09-09T15:00:10Z"),
                snapshot(ACCEPTED, 2, "2026-09-09T15:20:10Z")));

        add(document("D-007", "CUST-A", 1, 3), container("C-007", "D-007",
                snapshot(SENT, 1, "2026-09-10T09:00:10Z"),
                snapshot(DELIVERED, 2, "2026-09-10T18:00:00Z"),
                snapshot(ACCEPTED, 3, "2026-09-11T09:00:10Z")));

        // Add a document without a container, to simulate a missing container.
        documentsByIdMap.put("D-NC", document("D-NC", "CUST-A", 1));

        // Add the webhook configurations. CUST-C has no configuration on purpose.
        // CUST-A: enabled, BASIC authentication.
        configurationsByCustomerMap.put("CUST-A", new WebhookConfiguration("CUST-A", true, CUST_A_URL,
                WebhookAuthType.BASIC));

        // CUST-B: enabled, a different URL and OAUTH2 authentication.
        configurationsByCustomerMap.put("CUST-B", new WebhookConfiguration("CUST-B", true,
                URI.create("https://api.cliente-b.example/v2/notify"), WebhookAuthType.OAUTH2));

        // CUST-D: it has a configuration, but the webhook is disabled.
        configurationsByCustomerMap.put("CUST-D", new WebhookConfiguration("CUST-D", false,
                URI.create("https://cliente-d.example/hook"), WebhookAuthType.HEADER));
    }

    @Test
    void queuesTheResolvedStateWithTheCustomerWebhook() {
        List<ItemResult> results = service().send(List.of(item("D-001", null)));

        assertThat(results).singleElement().isInstanceOf(ItemResult.Queued.class);
        assertThat(queued).singleElement().satisfies(message -> {
            assertThat(message.requestId()).isEqualTo("D-001");
            assertThat(message.customerId()).isEqualTo("CUST-A");
            assertThat(message.containerId()).isEqualTo("C-001");
            assertThat(message.document().version()).isEqualTo(3);
            assertThat(message.containerStatus()).isEqualTo(ACCEPTED);
            assertThat(message.webhookUrl()).isEqualTo(CUST_A_URL);
            assertThat(message.authType()).isEqualTo(WebhookAuthType.BASIC);
        });
    }

    @Test
    void returnsOneResultPerItemInRequestOrder() {
        List<OneShotItem> items = List.of(item("D-001", null), item("D-004", "DELIVERED"), item("D-999", null));

        List<ItemResult> results = service().send(items);

        assertThat(results).extracting(ItemResult::originalItem).containsExactlyElementsOf(items);
        assertThat(results.get(0)).isInstanceOf(ItemResult.Queued.class);
        assertThat(results.get(1)).isInstanceOf(ItemResult.Queued.class);
        assertFailed(results.get(2), FailedStep.DOCUMENT_LOOKUP, "document not found");
    }

    @Test
    void returnsNoResultForAnEmptyRequest() {
        assertThat(service().send(List.of())).isEmpty();
    }

    @Test
    void failsOnlyTheItemsWithoutRequestId() {
        List<ItemResult> results = service().send(List.of(item(null, null), item(" ", null), item("D-001", null)));

        assertFailed(results.get(0), FailedStep.DOCUMENT_LOOKUP, "requestId is missing");
        assertFailed(results.get(1), FailedStep.DOCUMENT_LOOKUP, "requestId is missing");
        assertThat(results.get(2)).isInstanceOf(ItemResult.Queued.class);
    }

    @Test
    void failsOnAnUnknownContainerStatus() {
        List<ItemResult> results = service().send(List.of(item("D-004", "PAID"), item("D-004", "delivered")));

        assertFailed(results.get(0), FailedStep.CONTAINER_LOOKUP, "unknown container status 'PAID'");
        assertFailed(results.get(1), FailedStep.CONTAINER_LOOKUP, "unknown container status 'delivered'");
        assertThat(queued).isEmpty();
    }

    @Test
    void mapsResolutionFailuresToTheLookupSteps() {
        List<ItemResult> results = service().send(List.of(item("D-007", "DELIVERED"), item("D-NC", null)));

        assertFailed(results.get(0), FailedStep.DOCUMENT_LOOKUP, "document version 2 not found in history");
        assertFailed(results.get(1), FailedStep.CONTAINER_LOOKUP, "no container associated with the document");
    }

    @Test
    void failsWhenTheCustomerHasNoUsableWebhook() {
        List<ItemResult> results = service().send(List.of(item("D-005", null), item("D-006", null)));

        assertFailed(results.get(0), FailedStep.CONFIG_RESOLUTION, "no webhook configuration for customer CUST-C");
        assertFailed(results.get(1), FailedStep.CONFIG_RESOLUTION, "webhook disabled for customer CUST-D");
        assertThat(queued).isEmpty();
    }

    @Test
    void failsTheStepWhoseSourceIsUnavailableWithoutStoppingTheOthers() {
        DocumentRepositoryPort working = documentRepositoryPort;
        documentRepositoryPort = id -> {
            if (id.equals("D-004")) {
                throw new IllegalStateException("connection refused");
            }
            return working.findByRequestId(id);
        };

        List<ItemResult> results = service().send(List.of(item("D-004", null), item("D-001", null)));

        assertFailed(results.get(0), FailedStep.DOCUMENT_LOOKUP, "document unavailable");
        assertThat(results.get(1)).isInstanceOf(ItemResult.Queued.class);
    }

    @Test
    void failsOnlyTheItemWhoseStateCannotBeResolved() {
        ContainerRepositoryPort working = containerRepositoryPort;
        containerRepositoryPort = id -> id.equals("D-004")
                ? Arrays.asList((Container) null)
                : working.findByDocumentRequestId(id);

        List<ItemResult> results = service().send(List.of(item("D-004", null), item("D-001", null)));

        assertFailed(results.get(0), FailedStep.CONTAINER_LOOKUP, "could not resolve the state to notify");
        assertThat(results.get(1)).isInstanceOf(ItemResult.Queued.class);
    }

    @Test
    void failsTheChannelStepWhenTheQueueRejectsTheMessage() {
        notificationQueuePort = message -> {
            throw new IllegalStateException("queue full");
        };

        List<ItemResult> results = service().send(List.of(item("D-001", null)));

        assertFailed(results.get(0), FailedStep.CHANNEL_SEND, "notification queue unavailable");
    }

    @Test
    void processesDuplicateItemsOnceAndAnswersEachOfThem() {
        List<OneShotItem> items = List.of(item("D-001", null), item("D-001", null), item("D-999", null),
                item("D-999", null), item("D-001", "ACCEPTED"));

        List<ItemResult> results = service().send(items);

        assertThat(results).extracting(ItemResult::originalItem).containsExactlyElementsOf(items);
        assertThat(results).extracting(result -> result.getClass().getSimpleName())
                .containsExactly("Queued", "Queued", "Failed", "Failed", "Queued");
        assertThat(documentLookups.get("D-999")).hasValue(1);
        assertThat(queued).hasSize(2);
    }

    @Test
    void processesItemsInParallelWithinTheConcurrencyLimit() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        documentRepositoryPort = id -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return Optional.empty();
        };
        List<OneShotItem> items = IntStream.rangeClosed(1, 20).mapToObj(i -> item("D-P" + i, null)).toList();

        List<ItemResult> results = service(4).send(items);

        assertThat(results).hasSize(20);
        assertThat(peak.get()).isBetween(2, 4);
    }

    @Test
    void copiesTheLoggingContextToTheItemThreads() {
        List<@Nullable String> seen = new CopyOnWriteArrayList<>();
        notificationQueuePort = message -> seen.add(MDC.get("correlationId"));

        MDC.put("correlationId", "req-42");
        try {
            service().send(List.of(item("D-001", null), item("D-004", null)));
        } finally {
            MDC.remove("correlationId");
        }

        assertThat(seen).containsExactly("req-42", "req-42");
    }

    @Test
    void rejectsANonPositiveConcurrencyLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> service(0));
    }

    private OneShotService service() {
        return service(8);
    }

    private OneShotService service(int maxConcurrentItems) {
        return new OneShotService(documentRepositoryPort, containerRepositoryPort, webhookConfigurationRepositoryPort,
                notificationQueuePort, maxConcurrentItems);
    }

    private void add(Document document, Container container) {
        documentsByIdMap.put(document.requestId(), document);
        containersByDocumentMap.put(document.requestId(), List.of(container));
    }

    private static void assertFailed(ItemResult result, FailedStep step, String error) {
        assertThat(result).isInstanceOfSatisfying(ItemResult.Failed.class, failed -> {
            assertThat(failed.step()).isEqualTo(step);
            assertThat(failed.error()).isEqualTo(error);
        });
    }

    private static OneShotItem item(@Nullable String requestId, @Nullable String containerStatus) {
        return new OneShotItem(requestId, containerStatus);
    }

    private static Document document(String requestId, String customerId, int... versions) {
        List<DocumentVersion> history = Arrays.stream(versions)
                .mapToObj(v -> new DocumentVersion(v, Instant.EPOCH, BigDecimal.TEN, Currency.getInstance("EUR")))
                .toList();
        return new Document(requestId, customerId, Arrays.stream(versions).max().orElse(0), history);
    }

    private static Container container(String containerId, String requestId, ContainerSnapshot... history) {
        ContainerSnapshot last = history[history.length - 1];
        return new Container(containerId, requestId, last.status(), last.documentVersion(), List.of(history));
    }

    private static ContainerSnapshot snapshot(ContainerStatus status, int documentVersion, String changedAt) {
        return new ContainerSnapshot(status, documentVersion, Instant.parse(changedAt));
    }

}

package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.WebhookAuthType;

/**
 * The shape of dataset.json, read by Jackson. Jackson converts every value to its type and rejects a missing or null
 * one (see {@link DatasetLoader}); the records are then copied to the domain model, which stays independent of the
 * file format.
 */
record DatasetJson(
        List<DocumentJson> documents,
        List<ContainerJson> containers,
        List<WebhookConfigJson> webhookConfigs) {

    record DocumentJson(String requestId, String customerId, int currentVersion, List<VersionJson> versions) {
    }

    record VersionJson(int version, Instant updatedAt, BigDecimal amount, Currency currency) {
    }

    record ContainerJson(
            String containerId,
            String documentRequestId,
            ContainerStatus status,
            int documentVersion,
            List<SnapshotJson> history) {
    }

    record SnapshotJson(ContainerStatus status, int documentVersion, Instant changedAt) {
    }

    record WebhookConfigJson(String customerId, boolean enabled, URI url, AuthJson auth) {
    }

    record AuthJson(WebhookAuthType type) {
    }

}

package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The shape of dataset.json. Every value is nullable and kept as it appears in the file: the dataset is external data,
 * validated and converted to the domain model by {@link DatasetLoader}.
 */
record DatasetJson(
		@Nullable List<@Nullable DocumentJson> documents,
		@Nullable List<@Nullable ContainerJson> containers,
		@Nullable List<@Nullable WebhookConfigJson> webhookConfigs) {

	record DocumentJson(
			@Nullable String requestId,
			@Nullable String customerId,
			@Nullable Integer currentVersion,
			@Nullable List<@Nullable VersionJson> versions) {
	}

	record VersionJson(
			@Nullable Integer version,
			@Nullable String updatedAt,
			@Nullable String amount,
			@Nullable String currency) {
	}

	record ContainerJson(
			@Nullable String containerId,
			@Nullable String documentRequestId,
			@Nullable String status,
			@Nullable Integer documentVersion,
			@Nullable List<@Nullable SnapshotJson> history) {
	}

	record SnapshotJson(@Nullable String status, @Nullable Integer documentVersion, @Nullable String changedAt) {
	}

	record WebhookConfigJson(
			@Nullable String customerId,
			@Nullable Boolean enabled,
			@Nullable String url,
			@Nullable AuthJson auth) {
	}

	record AuthJson(@Nullable String type) {
	}

}

package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.ContainerJson;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.DocumentJson;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.SnapshotJson;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.VersionJson;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.WebhookConfigJson;
import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerSnapshot;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.model.WebhookAuthType;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads dataset.json and converts it to the domain model, failing on the first missing or invalid value with its
 * position in the file (e.g. {@code documents[2].customerId is missing}).
 * <p>
 * The dataset is read as is: rare cases such as gaps in a history or several containers per document are valid data,
 * only a value that cannot be represented in the domain model is rejected.
 */
public class DatasetLoader {

	private final ObjectMapper objectMapper;

	public DatasetLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Dataset load(Resource resource) {
		DatasetJson json;
		try (InputStream input = resource.getInputStream()) {
			json = objectMapper.readValue(input, DatasetJson.class);
		}
		catch (IOException | JacksonException e) {
			throw new InvalidDatasetException("Unable to read dataset " + resource.getDescription(), e);
		}
		return new Dataset(
				map(json.documents(), "documents", DatasetLoader::document),
				map(json.containers(), "containers", DatasetLoader::container),
				map(json.webhookConfigs(), "webhookConfigs", DatasetLoader::webhookConfiguration));
	}

	private static Document document(DocumentJson json, String path) {
		return new Document(
				require(json.requestId(), path + ".requestId"),
				require(json.customerId(), path + ".customerId"),
				require(json.currentVersion(), path + ".currentVersion"),
				map(json.versions(), path + ".versions", DatasetLoader::version));
	}

	private static DocumentVersion version(VersionJson json, String path) {
		return new DocumentVersion(
				require(json.version(), path + ".version"),
				parse(json.updatedAt(), path + ".updatedAt", Instant::parse),
				parse(json.amount(), path + ".amount", BigDecimal::new),
				parse(json.currency(), path + ".currency", Currency::getInstance));
	}

	private static Container container(ContainerJson json, String path) {
		return new Container(
				require(json.containerId(), path + ".containerId"),
				require(json.documentRequestId(), path + ".documentRequestId"),
				parse(json.status(), path + ".status", ContainerStatus::valueOf),
				require(json.documentVersion(), path + ".documentVersion"),
				map(json.history(), path + ".history", DatasetLoader::snapshot));
	}

	private static ContainerSnapshot snapshot(SnapshotJson json, String path) {
		return new ContainerSnapshot(
				parse(json.status(), path + ".status", ContainerStatus::valueOf),
				require(json.documentVersion(), path + ".documentVersion"),
				parse(json.changedAt(), path + ".changedAt", Instant::parse));
	}

	private static WebhookConfiguration webhookConfiguration(WebhookConfigJson json, String path) {
		return new WebhookConfiguration(
				require(json.customerId(), path + ".customerId"),
				require(json.enabled(), path + ".enabled"),
				parse(json.url(), path + ".url", URI::create),
				parse(require(json.auth(), path + ".auth").type(), path + ".auth.type", WebhookAuthType::valueOf));
	}

	/**
	 * Converts every element of a list, passing each one its position in the file for error messages.
	 */
	private static <J, T> List<T> map(@Nullable List<@Nullable J> elements, String path, ElementMapper<J, T> mapper) {
		List<@Nullable J> present = require(elements, path);
		List<T> result = new ArrayList<>(present.size());
		for (int i = 0; i < present.size(); i++) {
			String elementPath = path + "[" + i + "]";
			result.add(mapper.map(require(present.get(i), elementPath), elementPath));
		}
		return result;
	}

	private static <T> T require(@Nullable T value, String path) {
		if (value == null) {
			throw new InvalidDatasetException(path + " is missing");
		}
		return value;
	}

	private static <T> T parse(@Nullable String value, String path, Function<String, T> parser) {
		String present = require(value, path);
		try {
			return parser.apply(present);
		}
		catch (RuntimeException e) {
			throw new InvalidDatasetException(path + " is invalid: '" + present + "'", e);
		}
	}

	@FunctionalInterface
	private interface ElementMapper<J, T> {

		T map(J json, String path);

	}

}

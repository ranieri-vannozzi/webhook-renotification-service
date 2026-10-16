package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.Resource;

import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.ContainerJson;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.DocumentJson;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetJson.WebhookConfigJson;
import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerSnapshot;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Reads dataset.json and converts it to the domain model. Jackson does the parsing and the validation: a missing,
 * null or invalid value stops the startup with its position in the file.
 * <p>
 * The dataset is read as is: rare cases such as gaps in a history or several containers per document are valid data,
 * only a value that cannot be represented in the domain model is rejected.
 */
public class DatasetLoader {

    private final ObjectReader reader;

    /**
     * @param objectMapper the application mapper; the stricter settings apply to the dataset only, not to the HTTP
     *        requests, where optional fields may be missing
     */
    public DatasetLoader(ObjectMapper objectMapper) {
        this.reader = objectMapper.readerFor(DatasetJson.class)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
    }

    public Dataset load(Resource resource) {
        DatasetJson json;
        try (InputStream input = resource.getInputStream()) {
            json = reader.readValue(input);
        } catch (IOException | JacksonException e) {
            throw new InvalidDatasetException("Invalid dataset " + resource.getDescription() + ": " + e.getMessage(),
                    e);
        }
        return new Dataset(
                json.documents().stream().map(DatasetLoader::document).toList(),
                json.containers().stream().map(DatasetLoader::container).toList(),
                json.webhookConfigs().stream().map(DatasetLoader::webhookConfiguration).toList());
    }

    private static Document document(DocumentJson json) {
        List<DocumentVersion> versions = json.versions().stream()
                .map(version -> new DocumentVersion(version.version(), version.updatedAt(), version.amount(),
                        version.currency()))
                .toList();
        return new Document(json.requestId(), json.customerId(), json.currentVersion(), versions);
    }

    private static Container container(ContainerJson json) {
        List<ContainerSnapshot> history = json.history().stream()
                .map(snapshot -> new ContainerSnapshot(snapshot.status(), snapshot.documentVersion(),
                        snapshot.changedAt()))
                .toList();
        return new Container(json.containerId(), json.documentRequestId(), json.status(), json.documentVersion(),
                history);
    }

    private static WebhookConfiguration webhookConfiguration(WebhookConfigJson json) {
        return new WebhookConfiguration(json.customerId(), json.enabled(), json.url(), json.auth().type());
    }

}

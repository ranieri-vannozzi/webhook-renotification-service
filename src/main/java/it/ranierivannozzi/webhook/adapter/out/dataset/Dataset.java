package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.List;

import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

/**
 * The content of dataset.json, converted to the domain model.
 */
public record Dataset(
		List<Document> documents,
		List<Container> containers,
		List<WebhookConfiguration> webhookConfigurations) {

	public Dataset {
		documents = List.copyOf(documents);
		containers = List.copyOf(containers);
		webhookConfigurations = List.copyOf(webhookConfigurations);
	}

}

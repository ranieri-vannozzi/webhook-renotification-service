package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import it.ranierivannozzi.webhook.application.port.out.WebhookConfigurationRepository;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

/**
 * Webhook configurations of the dataset, indexed by customer. Read-only.
 */
public class InMemoryWebhookConfigurationRepository implements WebhookConfigurationRepository {

	private final Map<String, WebhookConfiguration> byCustomer = new HashMap<>();

	public InMemoryWebhookConfigurationRepository(List<WebhookConfiguration> configurations) {
		for (WebhookConfiguration configuration : configurations) {
			if (byCustomer.putIfAbsent(configuration.customerId(), configuration) != null) {
				throw new InvalidDatasetException("Duplicate webhook configuration for " + configuration.customerId());
			}
		}
	}

	@Override
	public Optional<WebhookConfiguration> findByCustomerId(String customerId) {
		return Optional.ofNullable(byCustomer.get(customerId));
	}

}

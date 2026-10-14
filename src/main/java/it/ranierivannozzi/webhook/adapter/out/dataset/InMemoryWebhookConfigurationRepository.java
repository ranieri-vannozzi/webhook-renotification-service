package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import it.ranierivannozzi.webhook.application.port.out.WebhookConfigurationRepository;
import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

/**
 * Webhook configurations of the dataset, indexed by customer. Read-only; every call waits the simulated latency.
 */
@Repository
public class InMemoryWebhookConfigurationRepository implements WebhookConfigurationRepository {

	private final SimulatedLatency latency;
	private final Map<String, WebhookConfiguration> byCustomer = new HashMap<>();

	public InMemoryWebhookConfigurationRepository(Dataset dataset, SimulatedLatency latency) {
		this.latency = latency;
		for (WebhookConfiguration configuration : dataset.webhookConfigurations()) {
			if (byCustomer.putIfAbsent(configuration.customerId(), configuration) != null) {
				throw new InvalidDatasetException("Duplicate webhook configuration for " + configuration.customerId());
			}
		}
	}

	@Override
	public Optional<WebhookConfiguration> findByCustomerId(String customerId) {
		latency.pause();
		return Optional.ofNullable(byCustomer.get(customerId));
	}

}

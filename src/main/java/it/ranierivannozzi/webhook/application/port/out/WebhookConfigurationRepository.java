package it.ranierivannozzi.webhook.application.port.out;

import java.util.Optional;

import it.ranierivannozzi.webhook.domain.model.WebhookConfiguration;

/**
 * Read access to the customers' webhook configurations. Implementations throw an unchecked exception when the
 * source is unavailable.
 */
public interface WebhookConfigurationRepository {

	Optional<WebhookConfiguration> findByCustomerId(String customerId);

}

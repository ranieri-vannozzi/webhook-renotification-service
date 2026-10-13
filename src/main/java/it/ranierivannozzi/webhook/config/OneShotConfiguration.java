package it.ranierivannozzi.webhook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.ranierivannozzi.webhook.adapter.out.dataset.Dataset;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetLoader;
import it.ranierivannozzi.webhook.adapter.out.dataset.InMemoryContainerRepository;
import it.ranierivannozzi.webhook.adapter.out.dataset.InMemoryDocumentRepository;
import it.ranierivannozzi.webhook.adapter.out.dataset.InMemoryWebhookConfigurationRepository;
import it.ranierivannozzi.webhook.adapter.out.queue.InMemoryNotificationQueue;
import it.ranierivannozzi.webhook.application.port.out.ContainerRepository;
import it.ranierivannozzi.webhook.application.port.out.DocumentRepository;
import it.ranierivannozzi.webhook.application.port.out.NotificationQueue;
import it.ranierivannozzi.webhook.application.port.out.WebhookConfigurationRepository;
import it.ranierivannozzi.webhook.application.service.OneShotService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the application service to its adapters. The application and domain classes carry no Spring annotation: this
 * is the only place that knows which adapter implements which port.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OneShotProperties.class)
class OneShotConfiguration {

	@Bean
	Dataset dataset(OneShotProperties properties, JsonMapper jsonMapper) {
		return new DatasetLoader(jsonMapper).load(properties.dataset());
	}

	@Bean
	InMemoryDocumentRepository documentRepository(Dataset dataset) {
		return new InMemoryDocumentRepository(dataset.documents());
	}

	@Bean
	InMemoryContainerRepository containerRepository(Dataset dataset) {
		return new InMemoryContainerRepository(dataset.containers());
	}

	@Bean
	InMemoryWebhookConfigurationRepository webhookConfigurationRepository(Dataset dataset) {
		return new InMemoryWebhookConfigurationRepository(dataset.webhookConfigurations());
	}

	@Bean
	InMemoryNotificationQueue notificationQueue() {
		return new InMemoryNotificationQueue();
	}

	@Bean
	OneShotService oneShotService(DocumentRepository documents, ContainerRepository containers,
			WebhookConfigurationRepository configurations, NotificationQueue queue, OneShotProperties properties) {
		return new OneShotService(documents, containers, configurations, queue, properties.maxConcurrentItems());
	}

}

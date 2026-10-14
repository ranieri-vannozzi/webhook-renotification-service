package it.ranierivannozzi.webhook.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Settings of the one-shot service, under the {@code oneshot} prefix.
 *
 * @param dataset where the source data is read from at startup
 * @param maxConcurrentItems how many items are processed at the same time across all requests
 * @param queueMaxAttempts how many deliveries a queued notification gets before going to the dead letters
 * @param queueInitialBackoff how long the first retry of a notification waits, doubled at each further one
 */
@ConfigurationProperties("oneshot")
public record OneShotProperties(
		@DefaultValue("classpath:dataset/dataset.json") Resource dataset,
		@DefaultValue("16") int maxConcurrentItems,
		@DefaultValue("5") int queueMaxAttempts,
		@DefaultValue("30s") Duration queueInitialBackoff) {
}

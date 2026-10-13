package it.ranierivannozzi.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Settings of the one-shot service, under the {@code oneshot} prefix.
 *
 * @param dataset where the source data is read from at startup
 * @param maxConcurrentItems how many items are processed at the same time across all requests
 */
@ConfigurationProperties("oneshot")
public record OneShotProperties(
		@DefaultValue("classpath:dataset/dataset.json") Resource dataset,
		@DefaultValue("16") int maxConcurrentItems) {
}

package it.ranierivannozzi.webhook.config;

import java.time.InstantSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import it.ranierivannozzi.webhook.adapter.out.dataset.Dataset;
import it.ranierivannozzi.webhook.adapter.out.dataset.DatasetLoader;

import tools.jackson.databind.json.JsonMapper;

/**
 * Beans that are not classes of this service: the dataset read at startup and the clock of the queue. Everything else
 * is a component found by the component scan.
 */
@Configuration(proxyBeanMethods = false)
class OneShotConfiguration {

	@Bean
	Dataset dataset(@Value("${oneshot.dataset}") Resource file, JsonMapper jsonMapper) {
		return new DatasetLoader(jsonMapper).load(file);
	}

	@Bean
	InstantSource instantSource() {
		return InstantSource.system();
	}

}

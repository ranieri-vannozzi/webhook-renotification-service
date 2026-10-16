package it.ranierivannozzi.webhook.adapter.out.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import it.ranierivannozzi.webhook.domain.model.Container;

import tools.jackson.databind.json.JsonMapper;

class InMemoryRepositoriesTests {

    private final Dataset dataset = new DatasetLoader(JsonMapper.builder().build())
            .load(new ClassPathResource("dataset/dataset.json"));

    @Test
    void findsDocumentsByRequestId() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository(dataset, SimulatedLatency.ZERO_LATENCY);

        assertThat(documents.findByRequestId("D-001")).hasValueSatisfying(d -> assertThat(d.customerId()).isEqualTo("CUST-A"));
        assertThat(documents.findByRequestId("D-999")).isEmpty();
    }

    @Test
    void findsEveryContainerOfADocument() {
        InMemoryContainerRepository containers = new InMemoryContainerRepository(dataset, SimulatedLatency.ZERO_LATENCY);

        assertThat(containers.findByDocumentRequestId("D-003")).extracting(Container::containerId)
                .containsExactly("C-003", "C-003B");
        assertThat(containers.findByDocumentRequestId("D-999")).isEmpty();
    }

    @Test
    void findsWebhookConfigurationsByCustomer() {
        InMemoryWebhookConfigurationRepository configurations = new InMemoryWebhookConfigurationRepository(dataset,
                SimulatedLatency.ZERO_LATENCY);

        assertThat(configurations.findByCustomerId("CUST-B")).isPresent();
        assertThat(configurations.findByCustomerId("CUST-C")).isEmpty();
    }

}

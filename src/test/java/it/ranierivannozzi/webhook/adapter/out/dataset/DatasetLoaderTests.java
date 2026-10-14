package it.ranierivannozzi.webhook.adapter.out.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.model.WebhookAuthType;

import tools.jackson.databind.json.JsonMapper;

class DatasetLoaderTests {

	private final DatasetLoader loader = new DatasetLoader(JsonMapper.builder().build());

	@Test
	void loadsTheProvidedDatasetIncludingItsRareCases() {
		Dataset dataset = loader.load(new ClassPathResource("dataset/dataset.json"));

		assertThat(dataset.documents()).hasSize(8);
		assertThat(dataset.containers()).hasSize(9);
		assertThat(dataset.webhookConfigurations()).hasSize(3);

		Document d007 = dataset.documents().stream().filter(d -> d.requestId().equals("D-007")).findFirst().orElseThrow();
		assertThat(d007.versions()).extracting(DocumentVersion::version).containsExactly(1, 3);
		assertThat(d007.versions().getFirst().amount()).isEqualTo(new BigDecimal("640.00"));
		assertThat(dataset.containers()).filteredOn(c -> c.documentRequestId().equals("D-003"))
				.extracting(c -> c.status())
				.containsExactly(ContainerStatus.DELIVERED, ContainerStatus.REJECTED);
		assertThat(dataset.webhookConfigurations()).filteredOn(c -> c.customerId().equals("CUST-D"))
				.singleElement()
				.satisfies(c -> {
					assertThat(c.enabled()).isFalse();
					assertThat(c.url()).isEqualTo(URI.create("https://cliente-d.example/hook"));
					assertThat(c.authType()).isEqualTo(WebhookAuthType.HEADER);
				});
	}

	@Test
	void rejectsAMissingValue() {
		String json = """
				{ "documents": [ { "requestId": "D-1", "currentVersion": 1, "versions": [] } ],
				  "containers": [], "webhookConfigs": [] }
				""";

		assertThatExceptionOfType(InvalidDatasetException.class).isThrownBy(() -> loader.load(resource(json)))
				.withMessageContaining("customerId")
				.withMessageContaining("documents");
	}

	@Test
	void rejectsAnInvalidValue() {
		String json = """
				{ "documents": [],
				  "containers": [ { "containerId": "C-1", "documentRequestId": "D-1", "status": "PAID",
				                    "documentVersion": 1, "history": [] } ],
				  "webhookConfigs": [] }
				""";

		assertThatExceptionOfType(InvalidDatasetException.class).isThrownBy(() -> loader.load(resource(json)))
				.withMessageContaining("PAID")
				.withMessageContaining("containers");
	}

	@Test
	void rejectsAFileThatIsNotJson() {
		assertThatExceptionOfType(InvalidDatasetException.class).isThrownBy(() -> loader.load(resource("not json")))
				.withMessageStartingWith("Invalid dataset");
	}

	private static ByteArrayResource resource(String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}

}

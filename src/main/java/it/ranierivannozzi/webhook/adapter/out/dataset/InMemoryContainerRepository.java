package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import it.ranierivannozzi.webhook.application.port.out.ContainerRepository;
import it.ranierivannozzi.webhook.domain.model.Container;

/**
 * Containers of the dataset, grouped by the document they belong to. Read-only.
 */
public class InMemoryContainerRepository implements ContainerRepository {

	private final Map<String, List<Container>> byDocument;

	public InMemoryContainerRepository(List<Container> containers) {
		this.byDocument = containers.stream().collect(Collectors.groupingBy(Container::documentRequestId));
	}

	@Override
	public List<Container> findByDocumentRequestId(String requestId) {
		return List.copyOf(byDocument.getOrDefault(requestId, List.of()));
	}

}

package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import it.ranierivannozzi.webhook.application.port.out.ContainerRepositoryPort;
import it.ranierivannozzi.webhook.domain.model.Container;

/**
 * Containers of the dataset, grouped by the document they belong to. Read-only; every call waits the simulated
 * latency.
 */
@Repository
public class InMemoryContainerRepository implements ContainerRepositoryPort {

    private final SimulatedLatency latency;
    private final Map<String, List<Container>> byDocumentMap;

    public InMemoryContainerRepository(Dataset dataset, SimulatedLatency latency) {
        this.latency = latency;
        this.byDocumentMap = dataset.containers().stream().collect(Collectors.groupingBy(Container::documentRequestId));
    }

    @Override
    public List<Container> findByDocumentRequestId(String requestId) {
        latency.pause();
        return List.copyOf(byDocumentMap.getOrDefault(requestId, List.of()));
    }

}

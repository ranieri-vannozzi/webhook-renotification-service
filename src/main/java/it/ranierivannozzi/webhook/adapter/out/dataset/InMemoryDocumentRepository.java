package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import it.ranierivannozzi.webhook.application.port.out.DocumentRepository;
import it.ranierivannozzi.webhook.domain.model.Document;

/**
 * Documents of the dataset, indexed by {@code requestId}. Read-only; every call waits the simulated latency.
 */
@Repository
public class InMemoryDocumentRepository implements DocumentRepository {

	private final SimulatedLatency latency;
	private final Map<String, Document> byRequestIdMap = new HashMap<>();

	public InMemoryDocumentRepository(Dataset dataset, SimulatedLatency latency) {
		this.latency = latency;
		for (Document document : dataset.documents()) {
			if (byRequestIdMap.putIfAbsent(document.requestId(), document) != null) {
				throw new InvalidDatasetException("Duplicate document " + document.requestId());
			}
		}
	}

	@Override
	public Optional<Document> findByRequestId(String requestId) {
		latency.pause();
		return Optional.ofNullable(byRequestIdMap.get(requestId));
	}

}

package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import it.ranierivannozzi.webhook.application.port.out.DocumentRepository;
import it.ranierivannozzi.webhook.domain.model.Document;

/**
 * Documents of the dataset, indexed by {@code requestId}. Read-only.
 */
public class InMemoryDocumentRepository implements DocumentRepository {

	private final Map<String, Document> byRequestId = new HashMap<>();

	public InMemoryDocumentRepository(List<Document> documents) {
		for (Document document : documents) {
			if (byRequestId.putIfAbsent(document.requestId(), document) != null) {
				throw new InvalidDatasetException("Duplicate document " + document.requestId());
			}
		}
	}

	@Override
	public Optional<Document> findByRequestId(String requestId) {
		return Optional.ofNullable(byRequestId.get(requestId));
	}

}

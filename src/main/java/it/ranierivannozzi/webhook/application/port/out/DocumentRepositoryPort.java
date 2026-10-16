package it.ranierivannozzi.webhook.application.port.out;

import java.util.Optional;

import it.ranierivannozzi.webhook.domain.model.Document;

/**
 * Read access to the document system. Implementations throw an unchecked exception when the source is unavailable.
 */
public interface DocumentRepositoryPort {

    Optional<Document> findByRequestId(String requestId);

}

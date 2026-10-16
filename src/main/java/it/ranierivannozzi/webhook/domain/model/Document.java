package it.ranierivannozzi.webhook.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * A fiscal document with its version history. {@code currentVersion} is the version the document system
 * declares as current; the history may have gaps.
 */
public record Document(String requestId, String customerId, int currentVersion, List<DocumentVersion> versions) {

    public Document {
        versions = List.copyOf(versions);
    }

    public Optional<DocumentVersion> findVersion(int version) {
        return versions.stream().filter(v -> v.version() == version).findFirst();
    }

}

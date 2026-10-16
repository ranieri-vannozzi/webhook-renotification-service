package it.ranierivannozzi.webhook.domain.resolution;

/**
 * Why the document state to notify could not be resolved.
 */
public enum ResolutionFailure {
    DOCUMENT_HAS_NO_VERSIONS,
    DOCUMENT_VERSION_NOT_FOUND,
    NO_CONTAINER,
    CONTAINER_STATUS_NOT_FOUND
}

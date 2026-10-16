package it.ranierivannozzi.webhook.domain.model;

import java.util.List;

/**
 * The processing state of a document, with the history of its state changes.
 */
public record Container(
        String containerId,
        String documentRequestId,
        ContainerStatus status,
        int documentVersion,
        List<ContainerSnapshot> history) {

    public Container {
        history = List.copyOf(history);
    }

}

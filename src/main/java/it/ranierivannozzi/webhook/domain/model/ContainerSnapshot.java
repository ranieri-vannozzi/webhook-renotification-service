package it.ranierivannozzi.webhook.domain.model;

import java.time.Instant;

/**
 * A container state change, recording which document version it referred to.
 */
public record ContainerSnapshot(ContainerStatus status, int documentVersion, Instant changedAt) {
}

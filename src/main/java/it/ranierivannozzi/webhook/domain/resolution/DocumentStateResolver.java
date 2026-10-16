package it.ranierivannozzi.webhook.domain.resolution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerSnapshot;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.resolution.Resolution.Failed;
import it.ranierivannozzi.webhook.domain.resolution.Resolution.Resolved;

/**
 * Chooses the document version and the container state to notify.
 * <p>
 * The snapshot is searched across all the containers of the document and the container is the one holding it, so a
 * document with several containers follows the same rules as one with a single container. When several snapshots
 * match, the most recent one wins.
 * <ul>
 * <li>Without a requested status: the highest version in the document history, and the most recent snapshot
 * referring to it. If no snapshot refers to it, the current state of the most recently changed container.</li>
 * <li>With a requested status: the most recent snapshot in that status, and the document version it refers to,
 * which must be in the document history.</li>
 * </ul>
 */
public final class DocumentStateResolver {

	/**
	 * Without a requested status, resolves the latest state of the document; with a requested status, the most
	 * recent time the document was in that status.
	 *
	 * @param document the document of the item
	 * @param containers all the containers of the document, possibly empty
	 * @param requestedStatus the status asked by the operator, or {@code null} for the latest state
	 * @return {@link Resolved} with the version, container and status to notify, or {@link Failed} with the reason
	 */
	public static Resolution resolve(Document document, List<Container> containers,
			@Nullable ContainerStatus requestedStatus) {
		if (requestedStatus == null) {
			// { "requestId": "D-001" }
			// { "requestId": "D-002" }
			return resolveLatest(document, containers);
		}
		// { "requestId": "D-004", "containerStatus": "DELIVERED" }
		return resolveByStatus(document, containers, requestedStatus);
	}

	/**
	 * No status on the request: the latest version of the document and its state.
	 * <p>
	 * Ex1: D-001 has versions 1, 2, 3;
	 * Ex1: C-001 has snapshots (v1, SENT), (v2, DELIVERED), (v3, ACCEPTED) --> v3 ACCEPTED.
	 * <p>
	 * Ex2: D-002 has versions 1, 2, 3, 4 but no container has a snapshot for v4;
	 * Ex2: so the current status of the most recently changed container (C-002) is used --> v4 DELIVERED (fallback).
	 */
	private static Resolution resolveLatest(Document document, List<Container> containers) {
		// 1. the highest version of the document
		DocumentVersion latest = highestVersion(document);
		if (latest == null) {
			return noLatestVersionFailedResolution();
		}
		if (containers.isEmpty()) {
			return noContainerFailedResolution();
		}

		// 2. the most recent snapshot referring to that version
		int latestVersion = latest.version();

		// 2a. the most recent container snapshot referring to that version, across all the containers
		SnapshotWithContainer match = mostRecentSnapshotWithContainer(containers,
				snapshot -> snapshot.documentVersion() == latestVersion);

		// 2b. if found, return the container and status of that container snapshot;
		// otherwise, return the current state of the most recently changed container
		if (match != null) {
			return documentStateResolved(latest, match.container(), match.snapshot().status(), false, containers);
		}

		// 3. no snapshot refers to it: the current state of the most recently changed container
		Container current = mostRecentlyChanged(containers);

		// 4. return the latest version, the most recently changed container, and its current status
		return documentStateResolved(latest, current, current.status(), true, containers);
	}

	/**
	 * A requested status on the request: the most recent snapshot in that status, and the document version it
	 * refers to.
	 * <p>
	 * Ex1 standard: D-001, DELIVERED -> has versions 1, 2, 3 ->  Resolved: v2, C-001, DELIVERED
	 * <p>
	 * Ex2 more containers: C-003 : SENT v1  11:00:10
	 * Ex2 more containers: C-003B: SENT v1  11:00:12 (2 seconds later) --> Resolved: v1, C-003B, SENT
	 * (C-003 is discarded, logged as a warning)
	 * <p>
	 * Ex3 status never reached: D-001, REJECTED --> Failed CONTAINER_STATUS_NOT_FOUND
	 * (the service translates it into CONTAINER_LOOKUP)
	 * <p>
	 * Ex4 version not found on doc: D-007, DELIVERED -> Failed DOCUMENT_VERSION_NOT_FOUND
	 * (the service translates it into DOCUMENT_LOOKUP)
	 */
	private static Resolution resolveByStatus(Document document, List<Container> containers,
			ContainerStatus requestedStatus) {

		// Containers are missing, so we cannot find a snapshot in the requested status.
		if (containers.isEmpty()) {
			return noContainerFailedResolution();
		}

		// 1. the most recent snapshot in the requested status
		SnapshotWithContainer match = mostRecentSnapshotWithContainer(containers,
				snapshot -> snapshot.status() == requestedStatus);

		// 1b. If not matched, return a failed resolution with the reason CONTAINER_STATUS_NOT_FOUND and a message.
		if (match == null) {
			return new Failed(ResolutionFailure.CONTAINER_STATUS_NOT_FOUND,
					"no container snapshot in status " + requestedStatus);
		}

		// 2. the document version that snapshot refers to.
		int versionNumber = match.snapshot().documentVersion();

		// 3. Get specific document version obj from "versions".
		Optional<DocumentVersion> version = document.findVersion(versionNumber);
		if (version.isEmpty()) {
			return new Failed(ResolutionFailure.DOCUMENT_VERSION_NOT_FOUND,
					"document version " + versionNumber + " not found in history");
		}

		// The resolved state of the document, with the version, container and status to notify, and the list of
		// discarded containers.
		return documentStateResolved(version.get(), match.container(), requestedStatus, false, containers);
	}

	/**
	 * Return the highest version of the document, or null if it has no versions.
	 */
	private static @Nullable DocumentVersion highestVersion(Document document) {
		DocumentVersion highest = null;
		for (DocumentVersion version : document.versions()) {
			if (highest == null || version.version() > highest.version()) {
				highest = version;
			}
		}
		return highest;
	}

	/**
	 * The most recent snapshot across all the containers, and the container that holds it, or null if none matches.
	 */
	private static @Nullable SnapshotWithContainer mostRecentSnapshotWithContainer(List<Container> containers,
			Predicate<ContainerSnapshot> filter) {
		SnapshotWithContainer best = null;
		for (Container container : containers) {
			for (ContainerSnapshot snapshot : container.history()) {
				if (filter.test(snapshot)
						&& (best == null || snapshot.changedAt().isAfter(best.snapshot().changedAt()))) {
					best = new SnapshotWithContainer(container, snapshot);
				}
			}
		}
		return best;
	}

	/**
	 * The container that was most recently changed, across all the containers; the list must not be empty.
	 */
	private static Container mostRecentlyChanged(List<Container> containers) {
		Container latest = containers.get(0);
		for (Container container : containers) {
			if (lastChange(container).isAfter(lastChange(latest))) {
				latest = container;
			}
		}
		return latest;
	}

	/**
	 * The last change of the container, or {@code Instant.MIN} if it has no snapshots.
	 */
	private static Instant lastChange(Container container) {
		Instant last = Instant.MIN;
		for (ContainerSnapshot snapshot : container.history()) {
			if (snapshot.changedAt().isAfter(last)) {
				last = snapshot.changedAt();
			}
		}
		return last;
	}

	/**
	 * The resolved state of the document, with the version, container and status to notify, and the list of
	 * discarded containers.
	 */
	private static Resolved documentStateResolved(DocumentVersion version, Container container, ContainerStatus status,
			boolean currentContainerFallback, List<Container> containers) {
		List<Container> discarded = new ArrayList<>();
		for (Container other : containers) {
			if (!other.equals(container)) {
				discarded.add(other);
			}
		}
		return new Resolved(version, container, status, currentContainerFallback, discarded);
	}

	/**
	 * The document has no versions, so it cannot be resolved.
	 */
	private static Failed noLatestVersionFailedResolution() {
		return new Failed(ResolutionFailure.DOCUMENT_HAS_NO_VERSIONS, "document has no versions");
	}

	/**
	 * The document has no containers, so it cannot be resolved.
	 */
	private static Failed noContainerFailedResolution() {
		return new Failed(ResolutionFailure.NO_CONTAINER, "no container associated with the document");
	}

	private record SnapshotWithContainer(Container container, ContainerSnapshot snapshot) {
	}

}

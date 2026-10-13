package it.ranierivannozzi.webhook.domain.resolution;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

import java.time.Instant;
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

	private DocumentStateResolver() {
	}

	public static Resolution resolve(Document document, List<Container> containers,
			@Nullable ContainerStatus requestedStatus) {
		return requestedStatus == null
				? resolveLatest(document, containers)
				: resolveByStatus(document, containers, requestedStatus);
	}

	private static Resolution resolveLatest(Document document, List<Container> containers) {
		Optional<DocumentVersion> latest = document.versions().stream().max(comparingInt(DocumentVersion::version));
		if (latest.isEmpty()) {
			return new Failed(ResolutionFailure.DOCUMENT_HAS_NO_VERSIONS, "document has no versions");
		}
		if (containers.isEmpty()) {
			return noContainer();
		}
		DocumentVersion version = latest.get();
		return mostRecentSnapshot(containers, snapshot -> snapshot.documentVersion() == version.version())
				.map(match -> resolved(version, match.container(), match.snapshot().status(), false, containers))
				.orElseGet(() -> {
					Container current = mostRecentlyChanged(containers);
					return resolved(version, current, current.status(), true, containers);
				});
	}

	private static Resolution resolveByStatus(Document document, List<Container> containers,
			ContainerStatus requestedStatus) {
		if (containers.isEmpty()) {
			return noContainer();
		}
		Optional<SnapshotMatch> match = mostRecentSnapshot(containers,
				snapshot -> snapshot.status() == requestedStatus);
		if (match.isEmpty()) {
			return new Failed(ResolutionFailure.CONTAINER_STATUS_NOT_FOUND,
					"no container snapshot in status " + requestedStatus);
		}
		int versionNumber = match.get().snapshot().documentVersion();
		return document.findVersion(versionNumber)
				.<Resolution>map(version -> resolved(version, match.get().container(), requestedStatus, false,
						containers))
				.orElseGet(() -> new Failed(ResolutionFailure.DOCUMENT_VERSION_NOT_FOUND,
						"document version " + versionNumber + " not found in history"));
	}

	private static Optional<SnapshotMatch> mostRecentSnapshot(List<Container> containers,
			Predicate<ContainerSnapshot> filter) {
		return containers.stream()
				.flatMap(container -> container.history().stream()
						.filter(filter)
						.map(snapshot -> new SnapshotMatch(container, snapshot)))
				.max(comparing(match -> match.snapshot().changedAt()));
	}

	private static Container mostRecentlyChanged(List<Container> containers) {
		return containers.stream()
				.max(comparing(DocumentStateResolver::lastChange))
				.orElseThrow();
	}

	private static Instant lastChange(Container container) {
		return container.history().stream()
				.map(ContainerSnapshot::changedAt)
				.max(Instant::compareTo)
				.orElse(Instant.MIN);
	}

	private static Resolved resolved(DocumentVersion version, Container container, ContainerStatus status,
			boolean currentContainerFallback, List<Container> containers) {
		List<Container> discarded = containers.stream().filter(other -> !other.equals(container)).toList();
		return new Resolved(version, container, status, currentContainerFallback, discarded);
	}

	private static Failed noContainer() {
		return new Failed(ResolutionFailure.NO_CONTAINER, "no container associated with the document");
	}

	private record SnapshotMatch(Container container, ContainerSnapshot snapshot) {
	}

}

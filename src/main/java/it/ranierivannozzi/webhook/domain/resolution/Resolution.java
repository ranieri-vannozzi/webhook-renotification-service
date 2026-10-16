package it.ranierivannozzi.webhook.domain.resolution;

import java.util.List;

import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;

/**
 * Outcome of {@link DocumentStateResolver#resolve}: either the document version and container state to notify,
 * or the reason they could not be determined.
 */
public sealed interface Resolution {

    /**
     * The state to notify.
     *
     * @param currentContainerFallback no snapshot matched the latest document version, so the current state of the
     *        most recently changed container was used
     * @param discardedContainers the other containers of the document, to be reported when there is more than one
     */
    record Resolved(
            DocumentVersion version,
            Container container,
            ContainerStatus status,
            boolean currentContainerFallback,
            List<Container> discardedContainers) implements Resolution {

        public Resolved {
            discardedContainers = List.copyOf(discardedContainers);
        }

    }

    /**
     * @param message readable by an operator
     */
    record Failed(ResolutionFailure reason, String message) implements Resolution {
    }

}

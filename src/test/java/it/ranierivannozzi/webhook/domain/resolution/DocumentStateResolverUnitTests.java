package it.ranierivannozzi.webhook.domain.resolution;

import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.ACCEPTED;
import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.DELIVERED;
import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.REJECTED;
import static it.ranierivannozzi.webhook.domain.model.ContainerStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import it.ranierivannozzi.webhook.domain.model.Container;
import it.ranierivannozzi.webhook.domain.model.ContainerSnapshot;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.Document;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.resolution.Resolution.Failed;
import it.ranierivannozzi.webhook.domain.resolution.Resolution.Resolved;

/**
 * Cases mirror the entries of dataset.json with the same identifiers.
 */
class DocumentStateResolverUnitTests {

    @Nested
    class WithoutRequestedStatus {

        @Test
        void picksTheSnapshotAlignedWithTheLatestVersion() {
            Document d001 = document("D-001", 1, 2, 3);
            Container c001 = container("C-001",
                    snapshot(SENT, 1, "2026-09-01T09:00:10Z"),
                    snapshot(DELIVERED, 2, "2026-09-01T09:05:10Z"),
                    snapshot(ACCEPTED, 3, "2026-09-02T14:30:10Z"));

            Resolved resolved = resolved(DocumentStateResolver.resolve(d001, List.of(c001), null));

            assertThat(resolved.version().version()).isEqualTo(3);
            assertThat(resolved.container()).isEqualTo(c001);
            assertThat(resolved.status()).isEqualTo(ACCEPTED);
            assertThat(resolved.currentContainerFallback()).isFalse();
            assertThat(resolved.discardedContainers()).isEmpty();
        }

        @Test
        void fallsBackToTheCurrentContainerWhenNoSnapshotIsAligned() {
            Document d002 = document("D-002", 1, 2, 3, 4);
            Container c002 = container("C-002",
                    snapshot(SENT, 1, "2026-09-03T08:00:10Z"),
                    snapshot(SENT, 2, "2026-09-03T08:02:10Z"),
                    snapshot(DELIVERED, 3, "2026-09-04T10:00:10Z"));

            Resolved resolved = resolved(DocumentStateResolver.resolve(d002, List.of(c002), null));

            assertThat(resolved.version().version()).isEqualTo(4);
            assertThat(resolved.container()).isEqualTo(c002);
            assertThat(resolved.status()).isEqualTo(DELIVERED);
            assertThat(resolved.currentContainerFallback()).isTrue();
        }

        @Test
        void ignoresGapsInTheHistoryBeforeTheLatestVersion() {
            Document d007 = document("D-007", 1, 3);
            Container c007 = c007();

            Resolved resolved = resolved(DocumentStateResolver.resolve(d007, List.of(c007), null));

            assertThat(resolved.version().version()).isEqualTo(3);
            assertThat(resolved.status()).isEqualTo(ACCEPTED);
        }

        @Test
        void picksTheContainerWithTheMostRecentAlignedSnapshotAndReportsTheOther() {
            Resolved resolved = resolved(DocumentStateResolver.resolve(d003(), List.of(c003(), c003b()), null));

            assertThat(resolved.version().version()).isEqualTo(2);
            assertThat(resolved.container()).isEqualTo(c003b());
            assertThat(resolved.status()).isEqualTo(REJECTED);
            assertThat(resolved.discardedContainers()).containsExactly(c003());
        }

        @Test
        void fallsBackToTheMostRecentlyChangedContainerAmongSeveral() {
            Document document = document("D-X", 1, 2, 3);
            Container older = container("C-OLD", snapshot(DELIVERED, 2, "2026-09-01T10:00:00Z"));
            Container newer = container("C-NEW", snapshot(REJECTED, 2, "2026-09-01T11:00:00Z"));

            Resolved resolved = resolved(DocumentStateResolver.resolve(document, List.of(older, newer), null));

            assertThat(resolved.container()).isEqualTo(newer);
            assertThat(resolved.status()).isEqualTo(REJECTED);
            assertThat(resolved.currentContainerFallback()).isTrue();
            assertThat(resolved.discardedContainers()).containsExactly(older);
        }

        @Test
        void failsWhenTheDocumentHasNoVersions() {
            Document document = document("D-X");

            Failed failed = failed(DocumentStateResolver.resolve(document, List.of(c007()), null));

            assertThat(failed.reason()).isEqualTo(ResolutionFailure.DOCUMENT_HAS_NO_VERSIONS);
        }

        @Test
        void failsWhenTheDocumentHasNoContainer() {
            Failed failed = failed(DocumentStateResolver.resolve(document("D-X", 1), List.of(), null));

            assertThat(failed.reason()).isEqualTo(ResolutionFailure.NO_CONTAINER);
        }

    }

    @Nested
    class WithRequestedStatus {

        @Test
        void picksTheSnapshotInThatStatusAndTheVersionItRefersTo() {
            Document d004 = document("D-004", 1, 2);
            Container c004 = container("C-004",
                    snapshot(SENT, 1, "2026-09-07T07:30:10Z"),
                    snapshot(DELIVERED, 2, "2026-09-07T07:31:10Z"));

            Resolved resolved = resolved(DocumentStateResolver.resolve(d004, List.of(c004), DELIVERED));

            assertThat(resolved.version().version()).isEqualTo(2);
            assertThat(resolved.container()).isEqualTo(c004);
            assertThat(resolved.status()).isEqualTo(DELIVERED);
            assertThat(resolved.currentContainerFallback()).isFalse();
        }

        @Test
        void picksTheMostRecentSnapshotWhenTheStatusRepeats() {
            Document d002 = document("D-002", 1, 2, 3, 4);
            Container c002 = container("C-002",
                    snapshot(SENT, 1, "2026-09-03T08:00:10Z"),
                    snapshot(SENT, 2, "2026-09-03T08:02:10Z"),
                    snapshot(DELIVERED, 3, "2026-09-04T10:00:10Z"));

            Resolved resolved = resolved(DocumentStateResolver.resolve(d002, List.of(c002), SENT));

            assertThat(resolved.version().version()).isEqualTo(2);
        }

        @Test
        void toleratesDuplicateSnapshotsForTheSameVersion() {
            Document d008 = document("D-008", 1, 2, 3);
            Container c008 = container("C-008",
                    snapshot(SENT, 1, "2026-09-12T10:00:10Z"),
                    snapshot(DELIVERED, 2, "2026-09-12T10:04:10Z"),
                    snapshot(DELIVERED, 2, "2026-09-12T19:30:00Z"),
                    snapshot(ACCEPTED, 3, "2026-09-13T08:15:10Z"));

            Resolved resolved = resolved(DocumentStateResolver.resolve(d008, List.of(c008), DELIVERED));

            assertThat(resolved.version().version()).isEqualTo(2);
            assertThat(resolved.status()).isEqualTo(DELIVERED);
        }

        @Test
        void picksTheOnlyContainerThatWentThroughThatStatus() {
            Resolved delivered = resolved(DocumentStateResolver.resolve(d003(), List.of(c003(), c003b()), DELIVERED));
            Resolved rejected = resolved(DocumentStateResolver.resolve(d003(), List.of(c003(), c003b()), REJECTED));

            assertThat(delivered.container()).isEqualTo(c003());
            assertThat(delivered.discardedContainers()).containsExactly(c003b());
            assertThat(rejected.container()).isEqualTo(c003b());
            assertThat(rejected.discardedContainers()).containsExactly(c003());
        }

        @Test
        void picksTheMostRecentSnapshotAcrossContainers() {
            Resolved resolved = resolved(DocumentStateResolver.resolve(d003(), List.of(c003(), c003b()), SENT));

            assertThat(resolved.container()).isEqualTo(c003b());
            assertThat(resolved.version().version()).isEqualTo(1);
        }

        @Test
        void failsWhenTheReferredVersionIsMissingFromTheHistory() {
            Failed failed = failed(DocumentStateResolver.resolve(document("D-007", 1, 3), List.of(c007()), DELIVERED));

            assertThat(failed.reason()).isEqualTo(ResolutionFailure.DOCUMENT_VERSION_NOT_FOUND);
            assertThat(failed.message()).contains("version 2");
        }

        @Test
        void failsWhenNoSnapshotIsInThatStatus() {
            Document d005 = document("D-005", 1);
            Container c005 = container("C-005", snapshot(SENT, 1, "2026-09-08T12:00:10Z"));

            Failed failed = failed(DocumentStateResolver.resolve(d005, List.of(c005), ACCEPTED));

            assertThat(failed.reason()).isEqualTo(ResolutionFailure.CONTAINER_STATUS_NOT_FOUND);
            assertThat(failed.message()).contains("ACCEPTED");
        }

        @Test
        void failsWhenTheDocumentHasNoContainer() {
            Failed failed = failed(DocumentStateResolver.resolve(document("D-X", 1), List.of(), SENT));

            assertThat(failed.reason()).isEqualTo(ResolutionFailure.NO_CONTAINER);
        }

    }

    private static Document d003() {
        return document("D-003", 1, 2);
    }

    private static Container c003() {
        return container("C-003",
                snapshot(SENT, 1, "2026-09-06T11:00:10Z"),
                snapshot(DELIVERED, 2, "2026-09-06T11:10:10Z"));
    }

    private static Container c003b() {
        return container("C-003B",
                snapshot(SENT, 1, "2026-09-06T11:00:12Z"),
                snapshot(REJECTED, 2, "2026-09-06T11:10:12Z"));
    }

    private static Container c007() {
        return container("C-007",
                snapshot(SENT, 1, "2026-09-10T09:00:10Z"),
                snapshot(DELIVERED, 2, "2026-09-10T18:00:00Z"),
                snapshot(ACCEPTED, 3, "2026-09-11T09:00:10Z"));
    }

    private static Document document(String requestId, int... versions) {
        List<DocumentVersion> history = Arrays.stream(versions)
                .mapToObj(v -> new DocumentVersion(v, Instant.EPOCH, BigDecimal.TEN, Currency.getInstance("EUR")))
                .toList();
        int current = Arrays.stream(versions).max().orElse(0);
        return new Document(requestId, "CUST-A", current, history);
    }

    /**
     * The current status and version of the container are those of its last snapshot.
     */
    private static Container container(String containerId, ContainerSnapshot... history) {
        ContainerSnapshot last = history[history.length - 1];
        return new Container(containerId, "D-X", last.status(), last.documentVersion(), List.of(history));
    }

    private static ContainerSnapshot snapshot(ContainerStatus status, int documentVersion, String changedAt) {
        return new ContainerSnapshot(status, documentVersion, Instant.parse(changedAt));
    }

    private static Resolved resolved(Resolution resolution) {
        assertThat(resolution).isInstanceOf(Resolved.class);
        return (Resolved) resolution;
    }

    private static Failed failed(Resolution resolution) {
        assertThat(resolution).isInstanceOf(Failed.class);
        return (Failed) resolution;
    }

}

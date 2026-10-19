package it.ranierivannozzi.webhook.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import it.ranierivannozzi.webhook.domain.model.ContainerStatus;
import it.ranierivannozzi.webhook.domain.model.DocumentVersion;
import it.ranierivannozzi.webhook.domain.model.NotificationMessage;
import it.ranierivannozzi.webhook.domain.model.WebhookAuthType;

class InMemoryNotificationQueueUnitTests {

    // The wait before the first retry. It doubles at each further retry: 30s, 1m, 2m, ...
    // It is a test value: in the application it comes from application.yaml (oneshot.queue-initial-backoff).
    private static final Duration BACKOFF = Duration.ofSeconds(30);

    // The current time seen by the queue. The test moves it forward with advance(), so it never waits.
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);

    // A fake clock: when the queue asks for the time, it gets the value of now.
    private final InstantSource clock = now::get;

    // A queue with at most 3 delivery attempts (the first one and 2 retries) and a first backoff of 30 seconds.
    private final InMemoryNotificationQueue queue = new InMemoryNotificationQueue(clock, 3, BACKOFF);

    @Test
    void deliversTheMessagesInArrivalOrder() {
        NotificationMessage first = message("D-001");
        NotificationMessage second = message("D-004");

        queue.enqueue(first);
        queue.enqueue(second);

        assertThat(queue.messages()).containsExactly(first, second);
        assertThat(queue.poll()).hasValueSatisfying(queued -> {
            assertThat(queued.message()).isEqualTo(first);
            assertThat(queued.attempt()).isEqualTo(1);
        });
        assertThat(queue.poll()).hasValueSatisfying(queued -> assertThat(queued.message()).isEqualTo(second));
        assertThat(queue.poll()).isEmpty();
    }

    @Test
    void retriesWithTheSameIdAfterAnExponentialBackoff() {
        queue.enqueue(message("D-001"));
        QueuedNotification first = queue.poll().orElseThrow();

        queue.retry(first);
        advance(BACKOFF.minusSeconds(1));
        assertThat(queue.poll()).isEmpty();
        advance(Duration.ofSeconds(1));
        QueuedNotification second = queue.poll().orElseThrow();

        queue.retry(second);
        advance(BACKOFF.multipliedBy(2).minusSeconds(1));
        assertThat(queue.poll()).isEmpty();
        advance(Duration.ofSeconds(1));
        QueuedNotification third = queue.poll().orElseThrow();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(third.id()).isEqualTo(first.id());
        assertThat(third.attempt()).isEqualTo(3);
    }

    @Test
    void movesAMessageToTheDeadLettersAfterTheLastAttempt() {
        queue.enqueue(message("D-001"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            advance(Duration.ofHours(1));
            queue.retry(queue.poll().orElseThrow());
        }

        advance(Duration.ofHours(1));
        assertThat(queue.poll()).isEmpty();
        assertThat(queue.deadLetters()).singleElement().satisfies(dead -> {
            assertThat(dead.attempt()).isEqualTo(3);
            assertThat(dead.message().requestId()).isEqualTo("D-001");
        });
    }

    private void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    private static NotificationMessage message(String requestId) {
        DocumentVersion version = new DocumentVersion(1, Instant.EPOCH, BigDecimal.ONE, Currency.getInstance("EUR"));
        return new NotificationMessage(requestId, "CUST-A", "C-1", version, ContainerStatus.SENT,
                URI.create("https://hooks.example/a"), WebhookAuthType.BASIC);
    }

}

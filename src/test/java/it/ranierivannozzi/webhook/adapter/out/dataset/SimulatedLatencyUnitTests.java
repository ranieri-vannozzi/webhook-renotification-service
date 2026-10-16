package it.ranierivannozzi.webhook.adapter.out.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SimulatedLatencyUnitTests {

    @Test
    void waitsAtLeastTheMinimum() {
        SimulatedLatency latency = new SimulatedLatency(Duration.ofMillis(50), Duration.ofMillis(60));

        long start = System.nanoTime();
        latency.pause();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(50));
    }

    @Test
    void rejectsAnInvalidRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimulatedLatency(Duration.ofMillis(-1), Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimulatedLatency(Duration.ofSeconds(2), Duration.ofSeconds(1)));
    }

}

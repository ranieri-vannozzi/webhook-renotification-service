package it.ranierivannozzi.webhook.adapter.out.dataset;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Makes the in-memory repositories as slow as the systems they stand in for: each call waits a random time between
 * {@code min} and {@code max}.
 */
@Component
public class SimulatedLatency {

	/**
	 * No wait at all, for tests that do not care about timing.
	 */
	public static final SimulatedLatency NONE = new SimulatedLatency(Duration.ZERO, Duration.ZERO);

	private final long minMillis;
	private final long maxMillis;

	public SimulatedLatency(@Value("${oneshot.source-latency-min}") Duration min,
			@Value("${oneshot.source-latency-max}") Duration max) {
		if (min.isNegative() || max.compareTo(min) < 0) {
			throw new IllegalArgumentException("Latency must satisfy 0 <= min <= max, was " + min + " and " + max);
		}
		this.minMillis = min.toMillis();
		this.maxMillis = max.toMillis();
	}

	/**
	 * Blocks the calling thread for a random time within the range.
	 *
	 * @throws IllegalStateException if the thread is interrupted while waiting
	 */
	public void pause() {
		long millis = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
		if (millis == 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while simulating latency", e);
		}
	}

}

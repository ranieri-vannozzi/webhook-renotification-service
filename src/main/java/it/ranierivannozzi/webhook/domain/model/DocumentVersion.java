package it.ranierivannozzi.webhook.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * One version of a document as kept in its history.
 */
public record DocumentVersion(int version, Instant updatedAt, BigDecimal amount, Currency currency) {
}

package it.ranierivannozzi.webhook.adapter.out.dataset;

/**
 * The dataset could not be read or does not have the expected shape. Raised at startup, so the service does not run
 * on partial data.
 */
public class InvalidDatasetException extends RuntimeException {

    public InvalidDatasetException(String message) {
        super(message);
    }

    public InvalidDatasetException(String message, Throwable cause) {
        super(message, cause);
    }

}

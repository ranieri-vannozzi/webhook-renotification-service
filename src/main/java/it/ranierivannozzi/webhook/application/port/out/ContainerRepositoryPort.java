package it.ranierivannozzi.webhook.application.port.out;

import java.util.List;

import it.ranierivannozzi.webhook.domain.model.Container;

/**
 * Read access to the containers. Implementations throw an unchecked exception when the source is unavailable.
 */
public interface ContainerRepositoryPort {

	/**
	 * @return the containers associated with the document, usually one; empty when there is none
	 */
	List<Container> findByDocumentRequestId(String requestId);

}

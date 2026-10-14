package it.ranierivannozzi.webhook.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import it.ranierivannozzi.webhook.application.port.in.FailedStep;
import it.ranierivannozzi.webhook.application.port.in.ItemResult;
import it.ranierivannozzi.webhook.application.port.in.OneShotItem;
import it.ranierivannozzi.webhook.application.port.in.SendOneShotUseCase;

/**
 * The HTTP contract only: the use case is mocked.
 */
@WebMvcTest(OneShotController.class)
class OneShotControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private SendOneShotUseCase sendOneShot;

	@Test
	void writesOnlyTheFieldsTheContractDefinesForEachOutcome() {
		OneShotItem queued = new OneShotItem("D-001", null);
		OneShotItem failed = new OneShotItem("D-004", "delivered");
		OneShotItem withoutId = new OneShotItem(null, "SENT");
		given(sendOneShot.send(List.of(queued, failed, withoutId))).willReturn(List.of(
				new ItemResult.Queued(queued),
				new ItemResult.Failed(failed, FailedStep.CONTAINER_LOOKUP, "unknown container status 'delivered'"),
				new ItemResult.Failed(withoutId, FailedStep.DOCUMENT_LOOKUP, "requestId is missing")));

		assertThat(post("""
				[ { "requestId": "D-001" },
				  { "requestId": "D-004", "containerStatus": "delivered" },
				  { "containerStatus": "SENT" } ]
				""")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				[ { "requestId": "D-001", "outcome": "QUEUED" },
				  { "requestId": "D-004", "containerStatus": "delivered", "outcome": "FAILED",
				    "failedStep": "CONTAINER_LOOKUP", "error": "unknown container status 'delivered'" },
				  { "requestId": null, "containerStatus": "SENT", "outcome": "FAILED",
				    "failedStep": "DOCUMENT_LOOKUP", "error": "requestId is missing" } ]
				""");
	}

	@Test
	void turnsANullElementIntoAnItemWithoutRequestId() {
		OneShotItem empty = new OneShotItem(null, null);
		given(sendOneShot.send(List.of(empty))).willReturn(List.of(
				new ItemResult.Failed(empty, FailedStep.DOCUMENT_LOOKUP, "requestId is missing")));

		assertThat(post("[ null ]")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				[ { "requestId": null, "outcome": "FAILED",
				    "failedStep": "DOCUMENT_LOOKUP", "error": "requestId is missing" } ]
				""");
	}

	@Test
	void answersAnEmptyListWithAnEmptyList() {
		given(sendOneShot.send(List.of())).willReturn(List.of());

		assertThat(post("[]")).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
	}

	@Test
	void rejectsABodyThatIsNotAJsonArray() {
		assertThat(post("{ \"requestId\": \"D-001\" }")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("not json")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("")).hasStatus(HttpStatus.BAD_REQUEST);
		verify(sendOneShot, never()).send(anyList());
	}

	@Test
	void answersAnUnexpectedErrorWithAProblemBodyAndNoInternalDetail() {
		given(sendOneShot.send(anyList())).willThrow(new IllegalStateException("secret internal detail"));

		assertThat(post("[ { \"requestId\": \"D-001\" } ]"))
				.hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyText().doesNotContain("secret");
	}

	@Test
	void returnsTheCorrelationIdReceivedOrANewOne() {
		given(sendOneShot.send(anyList())).willReturn(List.of());

		assertThat(post("[]").header(CorrelationIdFilter.X_CORRELATION_ID, "op-tool-123"))
				.headers().hasValue(CorrelationIdFilter.X_CORRELATION_ID, "op-tool-123");
		assertThat(post("[]").header(CorrelationIdFilter.X_CORRELATION_ID, "forged\nlog line"))
				.headers().satisfies(headers -> assertThat(headers.getFirst(CorrelationIdFilter.X_CORRELATION_ID))
						.isNotBlank().isNotEqualTo("forged\nlog line"));
	}

	@Test
	void rejectsABodyThatIsNotJson() {
		assertThat(mvc.post().uri(ApiRoutes.WEBHOOK_ONESHOT).contentType(MediaType.TEXT_PLAIN).content("D-001"))
				.hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	private MockMvcTester.MockMvcRequestBuilder post(String body) {
		return mvc.post().uri(ApiRoutes.WEBHOOK_ONESHOT).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}

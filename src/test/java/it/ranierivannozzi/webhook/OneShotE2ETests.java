package it.ranierivannozzi.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import it.ranierivannozzi.webhook.adapter.in.web.ApiRoutes;
import it.ranierivannozzi.webhook.adapter.out.queue.InMemoryNotificationQueue;
import it.ranierivannozzi.webhook.domain.model.ContainerStatus;

/**
 * The whole application on the provided dataset, from the HTTP request to the internal queue. The simulated latency
 * of the sources is turned off to keep the test fast.
 */
@SpringBootTest(properties = {"oneshot.source-latency-min=0ms", "oneshot.source-latency-max=0ms"})
@AutoConfigureMockMvc
class OneShotE2ETests {

    // Sends HTTP requests to the whole application without starting a real server.
    @Autowired
    private MockMvcTester mvc;

    // The real queue bean of the application, read to check what the request enqueued.
    @Autowired
    private InMemoryNotificationQueue queue;

    @Test
    void answersTheContractExampleAndEnqueuesTheResolvedStates() {
        String request = """
                [
                  { "requestId": "D-001" },
                  { "requestId": "D-004", "containerStatus": "DELIVERED" },
                  { "requestId": "D-999" }
                ]
                """;

        assertThat(mvc.post().uri(ApiRoutes.WEBHOOK_ONESHOT).contentType(MediaType.APPLICATION_JSON).content(request))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        [
                          { "requestId": "D-001", "outcome": "QUEUED" },
                          { "requestId": "D-004", "containerStatus": "DELIVERED", "outcome": "QUEUED" },
                          { "requestId": "D-999", "outcome": "FAILED",
                            "failedStep": "DOCUMENT_LOOKUP", "error": "document not found" }
                        ]
                        """);

        assertThat(queue.messages()).filteredOn(m -> m.requestId().equals("D-001")).singleElement().satisfies(m -> {
            assertThat(m.document().version()).isEqualTo(3);
            assertThat(m.containerStatus()).isEqualTo(ContainerStatus.ACCEPTED);
        });
        assertThat(queue.messages()).filteredOn(m -> m.requestId().equals("D-004")).singleElement().satisfies(m -> {
            assertThat(m.document().version()).isEqualTo(2);
            assertThat(m.containerStatus()).isEqualTo(ContainerStatus.DELIVERED);
        });
    }

}

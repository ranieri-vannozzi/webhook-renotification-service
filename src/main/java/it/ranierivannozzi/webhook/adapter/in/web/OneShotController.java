package it.ranierivannozzi.webhook.adapter.in.web;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import it.ranierivannozzi.webhook.application.port.in.OneShotItem;
import it.ranierivannozzi.webhook.application.port.in.OneShotServicePort;

/**
 * {@code POST /v1/webhook/oneshot}: answers 200 with one result per element, in the same order.
 * <p>
 * A body that is not a JSON array is rejected as a whole with 400; an empty array gets an empty array. Problems in a
 * single element (missing {@code requestId}, unknown {@code containerStatus}) fail only that element.
 */
@RestController
class OneShotController {

    private final OneShotServicePort servicePort;

    OneShotController(OneShotServicePort servicePort) {
        this.servicePort = servicePort;
    }

    @PostMapping(path = ApiRoutes.WEBHOOK_ONESHOT, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    List<OneShotItemResponse> send(@RequestBody List<@Nullable OneShotItemRequest> body) {
        List<OneShotItem> items = body.stream().map(OneShotWebMapper::toItem).toList();
        return servicePort.send(items).stream().map(OneShotWebMapper::toResponse).toList();
    }

}

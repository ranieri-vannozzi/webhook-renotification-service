package it.ranierivannozzi.webhook.adapter.in.web;

/**
 * Every HTTP route exposed by the service, in one place.
 */
public final class ApiRoutes {

	public static final String V1 = "/v1";
	public static final String WEBHOOK_ONESHOT = V1 + "/webhook/oneshot";

	private ApiRoutes() {
	}

}

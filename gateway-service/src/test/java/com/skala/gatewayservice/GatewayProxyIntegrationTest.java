package com.skala.gatewayservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayProxyIntegrationTest {

    private static final Queue<CapturedRequest> USER_REQUESTS = new ConcurrentLinkedQueue<>();
    private static final Queue<CapturedRequest> ORDER_REQUESTS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger USER_REQUEST_COUNT = new AtomicInteger();
    private static final AtomicInteger ORDER_REQUEST_COUNT = new AtomicInteger();

    private static final DisposableServer USER_SERVER = startServer("user", USER_REQUESTS, USER_REQUEST_COUNT);
    private static final DisposableServer ORDER_SERVER = startServer("order", ORDER_REQUESTS, ORDER_REQUEST_COUNT);

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:" + USER_SERVER.port());
        registry.add("ORDER_SERVICE_URL", () -> "http://127.0.0.1:" + ORDER_SERVER.port());
    }

    @BeforeEach
    void resetCapturedRequests() {
        USER_REQUESTS.clear();
        ORDER_REQUESTS.clear();
        USER_REQUEST_COUNT.set(0);
        ORDER_REQUEST_COUNT.set(0);
    }

    @AfterAll
    static void stopServers() {
        USER_SERVER.disposeNow();
        ORDER_SERVER.disposeNow();
    }

    @Test
    void routesCustomerRequestsOnlyToUserService() {
        webTestClient.get().uri("/api/customers/test")
                .accept(MediaType.APPLICATION_JSON)
                .cookie("session", "test-cookie")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("user");

        assertThat(USER_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/api/customers/test");
            assertThat(request.accept()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
            assertThat(request.cookie()).contains("session=test-cookie");
        });
        assertThat(ORDER_REQUEST_COUNT).hasValue(0);
    }

    @Test
    void routesProductRequestsToOrderService() {
        webTestClient.get().uri("/api/products/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order");

        assertThat(ORDER_REQUESTS).singleElement().satisfies(request ->
                assertThat(request.path()).isEqualTo("/api/products/test"));
    }

    @Test
    void routesOrderRequestsToOrderService() {
        webTestClient.get().uri("/api/orders/test")
                .exchange()
                .expectStatus().isOk();

        assertThat(ORDER_REQUESTS).singleElement().satisfies(request ->
                assertThat(request.path()).isEqualTo("/api/orders/test"));
    }

    @Test
    void forwardsAuthorizationHeaderUnchanged() {
        webTestClient.get().uri("/api/orders/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk();

        assertThat(ORDER_REQUESTS).singleElement().satisfies(request ->
                assertThat(request.authorization()).isEqualTo("Bearer test-token"));
    }

    @Test
    void forwardsPostMethodContentTypeAndJsonBody() {
        String body = """
                {"items":[{"productId":1,"quantity":2}]}
                """;

        webTestClient.post().uri("/api/orders/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("order");

        assertThat(ORDER_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.contentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertEquals(body, request.body(), true);
        });
    }

    @Test
    void forwardsQueryParameters() {
        webTestClient.get().uri("/api/products/test?page=1&size=20")
                .exchange()
                .expectStatus().isOk();

        assertThat(ORDER_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/api/products/test");
            assertThat(request.query()).isEqualTo("page=1&size=20");
        });
    }

    @Test
    void doesNotExposeInternalApis() {
        webTestClient.post().uri("/internal/customers/1/points/refund")
                .exchange()
                .expectStatus().isNotFound();

        assertNoDownstreamRequests();
    }

    @Test
    void returnsNotFoundForUnknownPaths() {
        webTestClient.get().uri("/unknown")
                .exchange()
                .expectStatus().isNotFound();

        assertNoDownstreamRequests();
    }

    @Test
    void exposesHealthWithoutCallingDownstreamServices() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        assertNoDownstreamRequests();
    }

    private static void assertNoDownstreamRequests() {
        assertThat(USER_REQUEST_COUNT).hasValue(0);
        assertThat(ORDER_REQUEST_COUNT).hasValue(0);
    }

    private static DisposableServer startServer(
            String service, Queue<CapturedRequest> requests, AtomicInteger requestCount) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            requestCount.incrementAndGet();
                            requests.add(new CapturedRequest(
                                    request.method().name(),
                                    request.uri().contains("?")
                                            ? request.uri().substring(0, request.uri().indexOf('?'))
                                            : request.uri(),
                                    request.uri().contains("?")
                                            ? request.uri().substring(request.uri().indexOf('?') + 1)
                                            : null,
                                    request.requestHeaders().get(HttpHeaders.AUTHORIZATION),
                                    request.requestHeaders().get(HttpHeaders.CONTENT_TYPE),
                                    request.requestHeaders().get(HttpHeaders.ACCEPT),
                                    request.requestHeaders().get(HttpHeaders.COOKIE),
                                    body));
                            response.status(200).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                            return response.sendString(Mono.just("{\"service\":\"" + service + "\"}")).then();
                        }))
                .bindNow();
    }

    private record CapturedRequest(
            String method,
            String path,
            String query,
            String authorization,
            String contentType,
            String accept,
            String cookie,
            String body) {
    }
}

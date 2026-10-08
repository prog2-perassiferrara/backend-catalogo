package com.prog2.catalog.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class TechnicalIntegrationTestServer implements AutoCloseable {

    public static final String PASSWORD = "fictional-technical-password";
    public static final String REDIS_PASSWORD = "fictional-redis-password";
    public static final String TOKEN = token("{\"exp\":4102444800}");
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();
    private final BlockingQueue<Request> arrivals = new LinkedBlockingQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final String defaultBody;

    public TechnicalIntegrationTestServer() throws IOException {
        this(null);
    }

    public TechnicalIntegrationTestServer(String defaultBody) throws IOException {
        this.defaultBody = defaultBody;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void respond(int status, String body) {
        responses.add(new Response(status, body, null, false));
    }

    public void disconnect() {
        responses.add(new Response(200, "", null, true));
    }

    public void waitBeforeResponding(CountDownLatch release) {
        responses.add(new Response(200, login(TOKEN, "PROVISIONED"), release, false));
    }

    public Request awaitRequest() throws InterruptedException {
        Request request = arrivals.poll(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("Expected HTTP request did not arrive");
        }
        return request;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requests.add(request);
        arrivals.add(request);
        Response response = responses.poll();
        if (response == null) {
            response = new Response(defaultBody == null ? 503 : 200,
                    defaultBody == null ? "{}" : defaultBody, null, false);
        }
        try (exchange) {
            if (response.disconnect()) {
                return;
            }
            if (response.release() != null && !response.release().await(5, TimeUnit.SECONDS)) {
                return;
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static String token(String claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                .getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".fictional-signature";
    }

    public static String login(String token, String status) {
        return "{\"id_token\":\"" + token + "\",\"integration\":" + integration(status) + "}";
    }

    public static String integration(String status) {
        return """
                {"groupId":"test-group","provisioningStatus":"%s",
                 "redisHost":"redis.test","redisPort":6379,"redisUsername":"test-group",
                 "redisPassword":"fictional-redis-password","redisReadNamespace":"catedra:sync:*",
                 "kafkaBootstrapServers":"kafka.test:9092","kafkaConsumerGroupId":"test-group",
                 "kafkaCatalogTopic":"catedra.catalog.test-group","additionalCompatibleField":true}
                """.formatted(status);
    }

    public record Request(String method, String path, String authorization, String body) {
        @Override
        public String toString() {
            return "Request[method=" + method + ", path=" + path + "]";
        }
    }

    private record Response(int status, String body, CountDownLatch release, boolean disconnect) {
        @Override
        public String toString() {
            return "Response[status=" + status + "]";
        }
    }
}

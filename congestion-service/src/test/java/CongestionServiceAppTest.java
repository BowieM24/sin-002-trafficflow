package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CongestionServiceAppTest {

    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void setup() throws InterruptedException {
        // Start the Javalin app in a separate thread so it doesn't block the tests
        new Thread(() -> CongestionServiceApp.main(new String[0])).start();

        // Give the server 3 seconds to fully initialize
        Thread.sleep(3000);
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7022/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    public void testStateChangeMissingLevel() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7022/stage/state-change"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertEquals("Missing 'level' query parameter", response.body());
    }

    @Test
    public void testStateChangeInvalidFormat() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7022/stage/state-change?level=abc"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertEquals("Level must be valid integer", response.body());
    }

    @Test
    public void testStateChangeLevelTooHigh() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7022/stage/state-change?level=9"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertEquals("Congestion level must be between 0 and 8", response.body());
    }
}

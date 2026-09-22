package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoutingServiceAppTest {

    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void setup() throws InterruptedException {
        // Start the Javalin app in a separate thread so it doesn't block the tests
        new Thread(() -> RoutingServiceApp.main(new String[0])).start();
        
        // Give the server 3 seconds to fully initialize and connect to ActiveMQ
        Thread.sleep(3000);
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:7023/health"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    public void testRouteEstimateMissingParameter() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:7023/route/estimate"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertEquals("Missing 'intersection' query parameter", response.body());
    }

    @Test
    public void testRouteEstimateValidRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:7023/route/estimate?intersection=INT-01"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));
        
        // Verify the JSON body contains the expected keys
        String body = response.body();
        assertTrue(body.contains("\"intersection\": \"INT-01\""));
        assertTrue(body.contains("\"congestionLevel\""));
        assertTrue(body.contains("\"estimatedTravelTimeMins\""));
    }
} 
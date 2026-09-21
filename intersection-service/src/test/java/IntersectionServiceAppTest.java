
import co.wethinkcode.trafficflow.IntersectionServiceApp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntersectionServiceAppTest {

    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    public static void setup() throws InterruptedException {
        // Start the Javalin app on port 7021
        new Thread(() -> IntersectionServiceApp.main(new String[0])).start();
        Thread.sleep(3000);
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7021/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }
}

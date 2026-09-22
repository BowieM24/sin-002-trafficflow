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
} 
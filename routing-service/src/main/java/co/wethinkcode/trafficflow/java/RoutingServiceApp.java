package co.wethinkcode.trafficflow.java;

import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;
import co.wethinkcode.trafficflow.mq.MqConfig;

public class RoutingServiceApp {

    // Thread-safe variable to hold the latest congestion level received from MQ. Initilize at 0 (clear traffic).
    // Use AtomicInteger instead of standard int because we handle two different threads
    // access this at the same time: the HTTP server (reading it) and the ActiveMQ listener (updating it).
    // Atomic variables prevent memory collisions. Default is 0 (clear traffic).
    private static final AtomicInteger currentCongestionLevel = new AtomicInteger(0);

    public static void main(String[] args) {
        // Intilize app/server without starting it to avoid test race condition
        Javalin app = Javalin.create();
        // Register route health check endpoint to verify the service is running
        app.get("/health", ctx -> ctx.result("OK"));
        // Domain endpoint providing estimated travel time based on the live congestion state
        app.get("/route/estimate", ctx -> {
            String intersection = ctx.queryParam("intersection");

            // Validate incoming request
            if (intersection == null || intersection.isEmpty()) {
                ctx.status(400).result("Missing 'intersection' query parameter");
                return;
            }

            // Routing logic: Base time is 10 mins. Each congestion level adds 5 mins.
            // Use .get() to safely read the current value from the AtomicInteger
            int congestion = currentCongestionLevel.get();
            int estimatedTimeMins = 10 + (congestion * 5);

            // Construct a JSON response string dynamically
            String jsonResponse = String.format(
                    "{\"intersection\": \"%s\", \"congestionLevel\": %d, \"estimatedTravelTimeMins\": %d}",
                    intersection, congestion, estimatedTimeMins
            );
            // Return caluclated estimate to the client
            ctx.contentType("application/json");
            ctx.result(jsonResponse);
        });

        // Connect to the (message broker) ActiveMQ subscriber
        try {
            startCongestionSubscriber();
        } catch (JMSException e) {
            System.err.println("CRITICAL: Failed to connect to ActiveMQ broker: " + e.getMessage());
        }

        /// Start Server last
        /// After routes are registered and broker is listening, open port
        app.start(7023);
    }

    /**
     * Connects to the ActiveMQ broker as a consumer on the congestion topic. It
     * listens to the designated Topic and asynchronously updates the internal
     * state whenever the Congestion Service broadcasts a change.
     */
    private static void startCongestionSubscriber() throws JMSException {
        // Setup the connection to ActiveMQ server
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        // Create a non-transactional, auto-acknowledging session
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination topic = session.createTopic(MqConfig.TOPIC);

        // Create a consumer for the specific topic
        MessageConsumer consumer = session.createConsumer(topic);

        // Attach an aynchronous listener, runs in the background and fires
        // automatically everytime a new message hits the topic
        consumer.setMessageListener(message -> {
            // Validation check
            if (!(message instanceof TextMessage)) {
                return;
            }

            try {
                String payload = ((TextMessage) message).getText();
                System.out.println("RoutingService received update: " + payload);

                if (payload.contains("\"congestionLevel\"")) {
                    String[] parts = payload.split(":");
                    if (parts.length > 1) {
                        // Regular expression stripping everything that is not a number
                        String numberString = parts[1].replaceAll("[^0-9]", "");
                        // Takes freshly parsed integer and update AtomicInteger
                        currentCongestionLevel.set(Integer.parseInt(numberString));
                    }
                }
            } catch (JMSException | NumberFormatException e) {
                System.err.println("Failed to parse incoming MQ message: " + e.getMessage());
            }
        });

    }
}

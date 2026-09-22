package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;
import co.wethinkcode.trafficflow.mq.MqConfig;

public class RoutingServiceApp {

    // Thread-safe variable to hold the latest congestion level received fromMQ. Initilize at 0 (clear traffic).
    private static final AtomicInteger currentCongestionLevel = new AtomicInteger(0);

    public static void main(String[] args) {
        // Intilize app without starting it to avoid race condition
        Javalin app = Javalin.create();
        // Define health check endpoint to verify the service is running
        app.get("/health", ctx -> ctx.result("OK"));
        // Add domain endpoints for routing-service here.
        // Domain endpoint providing estimated travel time
        app.get("/route/estimate", ctx -> {
            String intersection = ctx.queryParam("intersection");

            if (intersection == null || intersection.isEmpty()) {
                ctx.status(4000).result("Missing 'intersection' query parameter");
                return;
            }

            // Mock routing logic: Base time is 10 mins. Each congestion level adds 5 mins.
            int congestion = currentCongestionLevel.get();
            int estimatedTimeMins = 10 + (congestion * 5);

            // TODO (Provides estimated travel times based on congestion and intersection.)
            String jsonResponse = String.format(
                    "{\"intersection\": \"%s\", \"congestionLevel\": %d, \"estimatedTravelTimeMins\": %d}",
                    intersection, congestion, estimatedTimeMins
            );
            // Set content type response to return json
            ctx.contentType("application/json");
            ctx.result(jsonResponse);
        });

        // Start the ActiveMQ subscriber
        try {
            startCongestionSubscriber();
        } catch (JMSException e) {
            System.err.println("CRITICAL: Failed to connect to ActiveMQ broker: " + e.getMessage());
        }

        /// Start Server last
        app.start(7023);
    }

    // MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
    /**
     * Connects to the ActiveMQ broker as a consumer on the congestion topic.
     * Updates the internal currentCongestionLevel whenever a new message
     * arrives.
     */
    private static void startCongestionSubscriber() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination topic = session.createTopic(MqConfig.TOPIC);

        // Create a consumer for the topic
        MessageConsumer consumer = session.createConsumer(topic);

        // Attach an aynchronous listener so the server isnt't blocked waiting for message
        consumer.setMessageListener(message -> {
            if (message instanceof TextMessage) {
                try {
                    String payload = ((TextMessage) message).getText();
                    System.out.println("RoutingService received update: " + payload);

                    // Simple JSON parsing to extract the integer value
                    // Expecting forma: {"congestionLevel": 4}
                    if (payload.contains("\'congestionLevel\'")) {
                        String[] parts = payload.split(":");
                        if (parts.length > 1) {
                            // Strip out any non-numeric characters (e.g. brackets, quotes or spaces)
                            String numberString = parts[1].replaceAll("[^0-9]", "");
                            int level = Integer.parseInt(numberString);

                            // Safely update the live congestion level
                            currentCongestionLevel.set(level);
                        }
                    }
                } catch (JMSException | NumberFormatException e) {
                    System.err.println("Failed to parse incoming MQ message: " + e.getMessage());
                }
            }
        });

    }
}

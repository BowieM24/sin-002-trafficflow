package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import co.wethinkcode.trafficflow.mq.MqConfig;

public class CongestionServiceApp {

    public static void main(String[] args) {
        // Create the Javalin app and initialize without starting it immediately, allowing for route registration before the server starts
        Javalin app = Javalin.create();
        // Register route for health check endpoint
        app.get("/health", ctx -> ctx.result("OK"));
        // Add domain endpoints for congestion-service here.
        // Stage change endpoint accepting a congestion level (0-8)
        app.post("/stage/state-change", ctx -> {
            // Extract the value from the URL returned as a raw String
            String levelParam = ctx.queryParam("level");

            /// Input Validation, If the level Parameter is NULL or Empty throw an exception
            if (levelParam == null || levelParam.isEmpty()) {
                ctx.status(400).result("Missing 'level' query parameter");
                return;
            }

            try {
                int level = Integer.parseInt(levelParam);
                // If Level is smaller than 0 or bigger than 8 throw an exception
                if (level < 0 || level > 8) {
                    ctx.status(400).result("Congestion level must be between 0 and 8");
                    return;
                }

                publishCongestionLevel(level);
                ctx.result("Congestion level updated to " + level + " and published to MQ");

            } catch (NumberFormatException e) {
                ctx.status(400).result("Level must be valid integer");
            } catch (JMSException e) {
                ctx.status(500).result("Failed to publish to ActiveMQ: " + e.getMessage());
            }
        });

        /// Start the sever last
        app.start(7022);
    }

    /**
     * Connects to the ActiveMQ broker and pulishes the new congestion level to
     * the designated topic.
     */
    private static void publishCongestionLevel(int level) throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

        /// Try-with-resources ensures the connection and session are cleanly closed after sending
        try (Connection connection = connectionFactory.createConnection(); Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            connection.start();

            Destination destination = session.createTopic(MqConfig.TOPIC);

            try (MessageProducer producer = session.createProducer(destination)) {
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                /// Format payload as JSON string
                    String jsonPayload = String.format("{\"congestionLevel\": %d}", level);
                TextMessage message = session.createTextMessage(jsonPayload);

                producer.send(message);
                System.out.println("Published congestion state change: " + jsonPayload);
            }
        }
    }
}

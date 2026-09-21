package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import co.wethinkcode.trafficflow.mq.MqConfig;

 
public class IntersectionServiceApp {

    private static Connection connection;
    private static Session session;
    private static MessageProducer producer;

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Validates intersection/district names (source of truth).)
        // Add domain endpoints for intersection-service here.
        try {
            startHeartbeatPublisher();
        } catch (JMSException e) {
            System.err.println("Failed to start heartbeat publisher: " + e.getMessage());
        }
    }

    private static void startHeartbeatPublisher() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        /// Use createQueue as specified
        Destination destination = session.createQueue(MqConfig.HEARTBEAT_QUEUE);

        producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        /// Schedule a heartbeat to publish every 5 seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String payload = "{\'service\': \'intersection-service\', \'status\': \'ALIVE\', \'timestamp\': " + System.currentTimeMillis() + "}";
                TextMessage message = session.createTextMessage(payload);
                producer.send(message);
                System.out.println("Heartbeat sent: " + payload);
            } catch (JMSException e) {
                System.err.println("Failed to send: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
}


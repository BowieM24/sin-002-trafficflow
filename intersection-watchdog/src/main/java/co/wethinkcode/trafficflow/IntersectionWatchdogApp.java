package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import co.wethinkcode.trafficflow.mq.MqConfig;

public class IntersectionWatchdogApp {

    /// Threshold of 15 seconds before triggering an alert
    private static final long TIMEOUT_MILLIS = 15000;

    /// Thread-safe timestamp tracker initilized to the current time
    private static final AtomicLong lastHeartbeatTimestamp = new AtomicLong(System.currentTimeMillis());

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7024);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Cries for help if the Intersection Service crashes, since routes can no longer be validated.)
        // Mechanism: ActiveMQ Queue heartbeat/dead-letter
        try {
            startWatchdog();
        } catch (JMSException e) {
            System.err.println("Failed to start watchdog listener: " + e.getMessage());
        }
    }

    private static void startWatchdog() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Subscribe to the heartbeat queue
        Destination queue = session.createQueue(MqConfig.HEARTBEAT_QUEUE);
        MessageConsumer consumer = session.createConsumer(queue);

        // Update the timestamp every time a heartbeat arrives
        consumer.setMessageListener(message -> {
            if (message instanceof TextMessage) {
                lastHeartbeatTimestamp.set(System.currentTimeMillis());
                System.out.println("Watchdog: Heartbeat received from intersection-service.");
            }
        });

        // Start the background moniter task to detect missed heartbeats
        ScheduledExecutorService schedular = Executors.newSingleThreadScheduledExecutor();
        schedular.scheduleAtFixedRate(() -> {
            long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatTimestamp.get();
            if (timeSinceLastHeartbeat > TIMEOUT_MILLIS) {
                System.err.println("CRITICAL ALERT: Intersection Service has missed heartbeats! Last seen " + timeSinceLastHeartbeat + "ms ago.");
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
}

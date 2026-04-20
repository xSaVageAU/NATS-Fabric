package savage.natsfabric;

import io.nats.client.*;
import savage.natsfabric.config.NatsConfig;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Core NATS Manager for Fabric.
 * Handles the connection lifecycle and provides access to NATS primitives.
 */
public class NatsManager {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final NatsConfig config;
    private final ExecutorService natsExecutor;
    private volatile Connection natsConnection;
    private volatile JetStream jetStream;

    private NatsManager() {
        this.config = NatsConfig.load();
        this.natsExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public ExecutorService getExecutor() {
        return natsExecutor;
    }

    public static NatsManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final NatsManager INSTANCE = new NatsManager();
    }

    /**
     * Attempts to connect to the NATS server asynchronously.
     */
    public void connect() {
        try {
            NATSFabric.LOGGER.info("[NATS] Connecting to {} (ident: {})", config.natsUrl, config.serverName);

            Options.Builder builder = new Options.Builder()
                    .server(config.natsUrl)
                    .connectionName("FabricLibrary-" + config.serverName)
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(2))
                    .connectionListener((conn, type) ->
                            NATSFabric.LOGGER.info("[NATS] Connection event: {}", type))
                    .errorListener(new ErrorListener() {
                        @Override
                        public void errorOccurred(Connection conn, String error) {
                            NATSFabric.LOGGER.error("[NATS] Error: {}", error);
                        }
                    });

            if (config.natsAuthToken != null && !config.natsAuthToken.isEmpty()) {
                builder.token(config.natsAuthToken.toCharArray());
            } else if (config.natsUsername != null && !config.natsUsername.isEmpty()) {
                builder.userInfo(config.natsUsername.toCharArray(), config.natsPassword != null ? config.natsPassword.toCharArray() : new char[0]);
            }

            Options options = builder.build();

            natsConnection = Nats.connect(options);

            try {
                jetStream = natsConnection.jetStream();
                NATSFabric.LOGGER.info("[NATS] JetStream initialized");
            } catch (Exception e) {
                NATSFabric.LOGGER.warn("[NATS] JetStream unavailable: {}", e.getMessage());
            }

            NATSFabric.LOGGER.info("[NATS] Core connection established");
        } catch (Exception e) {
            NATSFabric.LOGGER.error("[NATS] Critical failure during connection", e);
        }
    }

    /**
     * Force a reload of the configuration and reconnect.
     */
    public void reload() {
        disconnect();
        config.save(); // ensure current state is on disk if modified in memory
        connect();
    }

    public void disconnect() {
        if (natsConnection != null) {
            try {
                natsConnection.close();
                NATSFabric.LOGGER.info("[NATS] Connection closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NATSFabric.LOGGER.error("[NATS] Shutdown interrupted", e);
            }
            natsConnection = null;
            jetStream = null;
        }

        natsExecutor.shutdown();
        try {
            if (!natsExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                natsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            natsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return the active NATS connection, or null if not connected.
     */
    public Connection getConnection() {
        return natsConnection;
    }

    /**
     * @return the active JetStream context, or null if JS is unavailable or not connected.
     */
    public JetStream getJetStream() {
        return jetStream;
    }

    public boolean isConnected() {
        Connection conn = natsConnection;
        return conn != null && conn.getStatus() == Connection.Status.CONNECTED;
    }

    public String getServerName() {
        return config.serverName;
    }
}

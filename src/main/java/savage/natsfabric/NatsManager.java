package savage.natsfabric;

import io.nats.client.*;
import savage.natsfabric.config.NatsConfig;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

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
     * Attempts to connect to the NATS server.
     * This will run in a background thread and retry indefinitely if the initial connection fails.
     */
    public void connect() {
        if (natsConnection != null && natsConnection.getStatus() != Connection.Status.CLOSED) return;
        if (isConnecting.getAndSet(true)) return;

        natsExecutor.execute(() -> {
            try {
                NATSFabric.LOGGER.info("[NATS-Lib] Starting persistent connection watchdog...");

                while (natsConnection == null || natsConnection.getStatus() == Connection.Status.CLOSED) {
                    try {
                        NATSFabric.LOGGER.info("[NATS-Lib] Connecting to {} (ident: {})", config.natsUrl, config.serverName);

                        Options.Builder builder = new Options.Builder()
                                .server(config.natsUrl)
                                .connectionName("FabricLibrary-" + config.serverName)
                                .maxReconnects(-1) // Infinite reconnects once connected
                                .reconnectWait(Duration.ofSeconds(2))
                                .connectionListener((conn, type) -> {
                                    NATSFabric.LOGGER.info("[NATS-Lib] Connection event: {}", type);
                                    if (type == ConnectionListener.Events.CONNECTED) {
                                        savage.natsfabric.event.NatsConnectionEvents.CONNECTED.invoker().onConnected(conn);
                                    } else if (type == ConnectionListener.Events.RECONNECTED) {
                                        savage.natsfabric.event.NatsConnectionEvents.RECONNECTED.invoker().onReconnected(conn);
                                    } else if (type == ConnectionListener.Events.DISCONNECTED) {
                                        savage.natsfabric.event.NatsConnectionEvents.DISCONNECTED.invoker().onDisconnected(conn);
                                    }
                                })
                                .errorListener(new ErrorListener() {
                                    @Override
                                    public void errorOccurred(Connection conn, String error) {
                                        NATSFabric.LOGGER.error("[NATS-Lib] Error: {}", error);
                                    }
                                });

                        if (config.natsAuthToken != null && !config.natsAuthToken.isEmpty()) {
                            builder.token(config.natsAuthToken.toCharArray());
                        } else if (config.natsUsername != null && !config.natsUsername.isEmpty()) {
                            builder.userInfo(config.natsUsername.toCharArray(), config.natsPassword != null ? config.natsPassword.toCharArray() : new char[0]);
                        }

                        // Synchronous connect call within our loop
                        natsConnection = Nats.connect(builder.build());

                        try {
                            jetStream = natsConnection.jetStream();
                            NATSFabric.LOGGER.info("[NATS-Lib] JetStream initialized");
                        } catch (Exception e) {
                            NATSFabric.LOGGER.warn("[NATS-Lib] JetStream unavailable: {}", e.getMessage());
                        }

                        NATSFabric.LOGGER.info("[NATS-Lib] Core connection established");
                        break; // Exit loop on success
                    } catch (Exception e) {
                        NATSFabric.LOGGER.error("[NATS-Lib] Initial connection failed: {}. Retrying in 5 seconds...", e.getMessage());
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            } finally {
                isConnecting.set(false);
            }
        });
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
                NATSFabric.LOGGER.info("[NATS-Lib] Connection closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                NATSFabric.LOGGER.error("[NATS-Lib] Shutdown interrupted", e);
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

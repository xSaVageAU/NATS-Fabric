package savage.natsfabric;

import io.nats.client.*;
import savage.natsfabric.config.NatsConfig;
import savage.natsfabric.event.NatsConnectionEvents;

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

    private NatsConfig config;
    private final ExecutorService natsExecutor;
    private volatile Connection natsConnection;
    private volatile JetStream jetStream;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    // --- Static access ---

    public static NatsManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final NatsManager INSTANCE = new NatsManager();
    }

    // --- Constructor ---

    private NatsManager() {
        this.config = NatsConfig.load();
        this.natsExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // --- Lifecycle ---

    /**
     * Attempts to connect to the NATS server.
     * Runs in a background thread and retries until the initial connection succeeds.
     */
    public void connect() {
        Connection conn = natsConnection;
        if (conn != null && conn.getStatus() != Connection.Status.CLOSED) return;
        if (isConnecting.getAndSet(true)) return;

        natsExecutor.execute(() -> {
            try {
                NATSFabric.LOGGER.info("[NATS-Lib] Starting initial connection attempt...");

                while (natsConnection == null || natsConnection.getStatus() == Connection.Status.CLOSED) {
                    try {
                        NATSFabric.LOGGER.info("[NATS-Lib] Connecting to {} (ident: {})", config.natsUrl, config.serverName);
                        natsConnection = Nats.connect(buildOptions());

                        try {
                            jetStream = natsConnection.jetStream();
                            NATSFabric.LOGGER.info("[NATS-Lib] JetStream initialized");
                        } catch (Exception e) {
                            NATSFabric.LOGGER.warn("[NATS-Lib] JetStream unavailable: {}", e.getMessage());
                        }

                        NATSFabric.LOGGER.info("[NATS-Lib] Core connection established");
                        break;
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
     * Closes the NATS connection.
     */
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
    }

    /**
     * Full teardown: disconnects and terminates the executor. Only call on final server stop.
     */
    public void shutdown() {
        disconnect();

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
     * Re-reads the config from disk and reconnects.
     */
    public void reload() {
        disconnect();
        this.config = NatsConfig.load();
        connect();
    }

    // --- Consumer API ---

    /** @return the active NATS connection, or null if not connected. */
    public Connection getConnection() {
        Connection conn = natsConnection;
        return conn != null && conn.getStatus() == Connection.Status.CONNECTED ? conn : null;
    }

    /** @return the active JetStream context, or null if JS is unavailable or not connected. */
    public JetStream getJetStream() {
        Connection conn = natsConnection;
        return conn != null && conn.getStatus() == Connection.Status.CONNECTED ? jetStream : null;
    }

    public boolean isConnected() {
        Connection conn = natsConnection;
        return conn != null && conn.getStatus() == Connection.Status.CONNECTED;
    }

    public String getServerName() {
        return config.serverName;
    }

    // --- Internals ---

    /** Returns the shared virtual-thread executor. */
    public ExecutorService getExecutor() {
        return natsExecutor;
    }

    /** Builds the NATS connection options from the current config. */
    private Options buildOptions() {
        Options.Builder builder = new Options.Builder()
                .server(config.natsUrl)
                .connectionName("FabricLibrary-" + config.serverName)
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .connectionListener((conn, type) -> {
                    NATSFabric.LOGGER.info("[NATS-Lib] Connection event: {}", type);
                    if (type == ConnectionListener.Events.CONNECTED) {
                        NatsConnectionEvents.CONNECTED.invoker().onConnected(conn);
                    } else if (type == ConnectionListener.Events.RECONNECTED) {
                        NatsConnectionEvents.RECONNECTED.invoker().onReconnected(conn);
                    } else if (type == ConnectionListener.Events.DISCONNECTED) {
                        NatsConnectionEvents.DISCONNECTED.invoker().onDisconnected(conn);
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
            builder.userInfo(config.natsUsername.toCharArray(),
                    config.natsPassword != null ? config.natsPassword.toCharArray() : new char[0]);
        }

        return builder.build();
    }
}

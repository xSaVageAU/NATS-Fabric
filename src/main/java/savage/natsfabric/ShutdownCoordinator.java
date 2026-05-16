package savage.natsfabric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates graceful shutdown by tracking registered NATS clients.
 * NatsManager waits for all registered clients to deregister before
 * closing the NATS connection.
 */
class ShutdownCoordinator {

    private final Map<String, Integer> registeredClients = new ConcurrentHashMap<>();

    // --- Registration ---

    /** Registers a client with no minimum timeout preference. */
    void registerClient(String modId) {
        registerClient(modId, 0);
    }

    /**
     * Registers a client with a minimum timeout requirement.
     * @param modId          The mod ID.
     * @param minimumSeconds Minimum seconds needed for cleanup.
     */
    void registerClient(String modId, int minimumSeconds) {
        registeredClients.put(modId, Math.max(0, minimumSeconds));
        NATSFabric.LOGGER.info("[NATS-Lib] Registered shutdown client: {} (min {}s)", modId, minimumSeconds);
    }

    /**
     * Updates the minimum timeout requirement for an already-registered client.
     * Logs an error if the client has not been registered.
     * @param modId          The mod ID.
     * @param minimumSeconds The new minimum seconds required.
     */
    void requestTimeout(String modId, int minimumSeconds) {
        if (!registeredClients.containsKey(modId)) {
            NATSFabric.LOGGER.error("[NATS-Lib] requestTimeout() called for unregistered client '{}'. Call registerClient() first.", modId);
            return;
        }
        registeredClients.put(modId, Math.max(0, minimumSeconds));
        NATSFabric.LOGGER.info("[NATS-Lib] Client '{}' updated minimum shutdown timeout to {}s", modId, minimumSeconds);
    }

    /** Signals that a client has completed its cleanup. */
    void deregisterClient(String modId) {
        if (registeredClients.remove(modId) != null) {
            NATSFabric.LOGGER.info("[NATS-Lib] Client deregistered: {}", modId);
        } else {
            NATSFabric.LOGGER.warn("[NATS-Lib] deregisterClient() called for unknown client: {}", modId);
        }
    }

    // --- Shutdown wait ---

    /**
     * Blocks until all registered clients deregister or the effective timeout expires.
     * Effective timeout = max(configTimeoutSeconds, highest client-requested timeout).
     *
     * @param configTimeoutSeconds The floor timeout from config.
     * @return true if all clients finished in time, false if timed out.
     */
    boolean awaitClients(int configTimeoutSeconds) {
        if (registeredClients.isEmpty()) return true;

        int effectiveTimeout = resolveTimeout(configTimeoutSeconds);
        NATSFabric.LOGGER.info("[NATS-Lib] Waiting up to {}s for {} client(s) to finish: {}",
                effectiveTimeout, registeredClients.size(), registeredClients.keySet());

        long deadline = System.currentTimeMillis() + effectiveTimeout * 1000L;
        while (!registeredClients.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!registeredClients.isEmpty()) {
            NATSFabric.LOGGER.warn("[NATS-Lib] Shutdown timeout reached. Forcing close. Still registered: {}", registeredClients.keySet());
            return false;
        }

        NATSFabric.LOGGER.info("[NATS-Lib] All clients finished. Proceeding with shutdown.");
        return true;
    }

    /** Resolves the effective timeout: max of config floor and all client requests. */
    private int resolveTimeout(int configTimeoutSeconds) {
        int maxClientRequest = registeredClients.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        return Math.max(configTimeoutSeconds, maxClientRequest);
    }
}

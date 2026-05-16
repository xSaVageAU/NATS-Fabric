package savage.natsfabric;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface for a shutdown hook that returns a CompletableFuture.
 * The NATS library will wait for this future to complete before closing the connection.
 */
@FunctionalInterface
public interface ShutdownHook {
    CompletableFuture<Void> onShutdown();
}

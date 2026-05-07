package savage.natsfabric.event;

import io.nats.client.Connection;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Events related to the NATS connection lifecycle.
 */
public final class NatsConnectionEvents {

    private NatsConnectionEvents() {}

    /**
     * Fired when the NATS connection is established or re-established.
     */
    public static final Event<Connected> CONNECTED = EventFactory.createArrayBacked(Connected.class, (listeners) -> (conn) -> {
        for (Connected listener : listeners) {
            listener.onConnected(conn);
        }
    });

    /**
     * Fired when the NATS connection is lost.
     */
    public static final Event<Disconnected> DISCONNECTED = EventFactory.createArrayBacked(Disconnected.class, (listeners) -> (conn) -> {
        for (Disconnected listener : listeners) {
            listener.onDisconnected(conn);
        }
    });

    /**
     * Fired when the NATS connection is restored after a loss.
     */
    public static final Event<Reconnected> RECONNECTED = EventFactory.createArrayBacked(Reconnected.class, (listeners) -> (conn) -> {
        for (Reconnected listener : listeners) {
            listener.onReconnected(conn);
        }
    });

    @FunctionalInterface
    public interface Connected {
        void onConnected(Connection connection);
    }

    @FunctionalInterface
    public interface Disconnected {
        void onDisconnected(Connection connection);
    }

    @FunctionalInterface
    public interface Reconnected {
        void onReconnected(Connection connection);
    }
}

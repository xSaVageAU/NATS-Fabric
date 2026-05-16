package savage.natsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod initializer for NATS-Fabric. Bootstraps the shared NATS connection on server start. */
public class NATSFabric implements ModInitializer {
	public static final String MOD_ID = "nats-fabric";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[NATS-Lib] Initializing Core NATS Library");

		NatsManager nats = NatsManager.getInstance();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("[NATS-Lib] Starting NATS connection...");
			nats.connect();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("[NATS-Lib] Server stopping. Initiating clean library shutdown...");
			nats.shutdown();
		});

		LOGGER.info("[NATS-Lib] Core Library Ready");
	}
}
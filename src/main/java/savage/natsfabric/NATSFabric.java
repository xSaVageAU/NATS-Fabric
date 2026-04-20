package savage.natsfabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NATSFabric implements ModInitializer {
	public static final String MOD_ID = "nats-fabric";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[NATS-Fabric] Initializing Core NATS Library");

		// Initialize NatsManager singleton config
		NatsManager.getInstance();

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("[NATS-Fabric] Starting NATS connection...");
			NatsManager.getInstance().connect();
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOGGER.info("[NATS-Fabric] Stopping NATS connection...");
			NatsManager.getInstance().disconnect();
		});

		LOGGER.info("[NATS-Fabric] Core Library Ready");
	}
}
package savage.natsfabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import savage.natsfabric.NATSFabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal configuration for the NATS connection.
 */
public class NatsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("nats-fabric.json");

    /** NATS server URL (e.g. {@code nats://127.0.0.1:4222}). */
    public String natsUrl = "nats://127.0.0.1:4222";

    /** Optional NATS authentication token. */
    public String natsAuthToken = "";

    /** Optional NATS username (if not using a token). */
    public String natsUsername = "";

    /** Optional NATS password. */
    public String natsPassword = "";

    /**
     * Unique name for this server instance.
     * Used for identifying the connection and durable consumer session naming.
     */
    public String serverName = "server-1";

    /**
     * Loads the config from disk, or creates a default one if it doesn't exist.
     */
    public static NatsConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            NatsConfig defaults = new NatsConfig();
            defaults.save();
            return defaults;
        }

        try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
            NatsConfig config = GSON.fromJson(reader, NatsConfig.class);
            return config != null ? config : new NatsConfig();
        } catch (IOException e) {
            NATSFabric.LOGGER.error("[NatsConfig] Failed to load config", e);
            return new NatsConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            NATSFabric.LOGGER.error("[NatsConfig] Failed to save config", e);
        }
    }
}

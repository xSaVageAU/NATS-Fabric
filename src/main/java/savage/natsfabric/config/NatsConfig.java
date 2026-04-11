package savage.natsfabric.config;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import savage.natsfabric.NATSFabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal configuration for the NATS connection.
 */
public class NatsConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("nats-fabric.yml");

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
            Yaml yaml = new Yaml();
            NatsConfig config = yaml.loadAs(reader, NatsConfig.class);
            return config != null ? config : new NatsConfig();
        } catch (Exception e) {
            NATSFabric.LOGGER.error("[NatsConfig] Failed to load config", e);
            return new NatsConfig();
        }
    }

    /**
     * Writes the default config to disk as a pre-formatted YAML template.
     * Only called once when the file does not yet exist.
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String template =
                    "# Unique name for this server instance\n" +
                    "serverName: server-1\n" +
                    "\n" +
                    "# NATS server connection URL\n" +
                    "natsUrl: nats://127.0.0.1:4222\n" +
                    "\n" +
                    "# Authentication - Token (leave blank if not used)\n" +
                    "natsAuthToken: ''\n" +
                    "\n" +
                    "# Authentication - Username/Password (leave blank if not used)\n" +
                    "natsUsername: ''\n" +
                    "natsPassword: ''\n";
            Files.writeString(CONFIG_PATH, template);
        } catch (Exception e) {
            NATSFabric.LOGGER.error("[NatsConfig] Failed to write default config", e);
        }
    }
}

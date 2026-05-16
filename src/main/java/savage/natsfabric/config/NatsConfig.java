package savage.natsfabric.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import savage.natsfabric.NATSFabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration for the NATS connection.
 */
public class NatsConfig {

    // --- Fields ---
    public String serverName           = "server-1";
    public String natsUrl              = "nats://127.0.0.1:4222";
    public String natsAuthToken        = "";
    public String natsUsername         = "";
    public String natsPassword         = "";
    public int    shutdownTimeoutSeconds = 10;

    // --- Config infrastructure ---

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("nats-fabric.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Ordered field definitions. When adding a new config field to this class,
     * add a matching entry here to ensure it is created and inserted correctly.
     */
    private static final List<ConfigField> CONFIG_FIELDS = List.of(
            new ConfigField("serverName",             "server-1"),
            new ConfigField("natsUrl",                "nats://127.0.0.1:4222"),
            new ConfigField("natsAuthToken",          ""),
            new ConfigField("natsUsername",           ""),
            new ConfigField("natsPassword",           ""),
            new ConfigField("shutdownTimeoutSeconds", "10")
    );

    private record ConfigField(String key, String defaultValue) {}

    // --- Public API ---

    /**
     * Loads the config from disk. Creates it on first run.
     * Inserts any missing fields (e.g. from a version update) in their correct position.
     * Throws if the file cannot be read or parsed — does not silently fall back to defaults.
     */
    public static NatsConfig load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
        } catch (IOException e) {
            throw new RuntimeException("[NatsConfig] Cannot create config directory: " + e.getMessage(), e);
        }

        if (!Files.exists(CONFIG_PATH)) {
            writeJson(buildJson(null));
            NATSFabric.LOGGER.info("[NatsConfig] Created default config at {}", CONFIG_PATH);
            return new NatsConfig();
        }

        JsonObject existing = parseJson();

        // Warn about unknown keys — likely a typo or a field removed in a newer version
        Set<String> knownKeys = CONFIG_FIELDS.stream()
                .map(ConfigField::key)
                .collect(Collectors.toSet());
        existing.keySet().stream()
                .filter(k -> !knownKeys.contains(k))
                .forEach(k -> NATSFabric.LOGGER.warn("[NatsConfig] Unknown field '{}' in config — possible typo?", k));

        // Detect missing fields (new version added options the user's file doesn't have yet)
        List<String> missing = CONFIG_FIELDS.stream()
                .filter(f -> !existing.has(f.key()))
                .map(ConfigField::key)
                .toList();

        // Rebuild in CONFIG_FIELDS order, inserting defaults for any missing fields
        JsonObject merged = buildJson(existing);

        if (!missing.isEmpty()) {
            NATSFabric.LOGGER.warn("[NatsConfig] New config fields detected, inserting defaults: {}", missing);
            writeJson(merged);
        }

        return fromJson(merged);
    }

    // --- Private helpers ---

    /**
     * Builds a JsonObject in CONFIG_FIELDS order.
     * Copies values from {@code existing} where present; uses field defaults otherwise.
     * Pass {@code null} for existing to generate a full-defaults object.
     */
    private static JsonObject buildJson(JsonObject existing) {
        JsonObject out = new JsonObject();
        for (ConfigField f : CONFIG_FIELDS) {
            if (existing != null && existing.has(f.key())) {
                out.add(f.key(), existing.get(f.key()));
            } else {
                out.addProperty(f.key(), f.defaultValue());
            }
        }
        return out;
    }

    private static NatsConfig fromJson(JsonObject obj) {
        NatsConfig cfg = new NatsConfig();
        cfg.serverName             = getString(obj, "serverName",             cfg.serverName);
        cfg.natsUrl                = getString(obj, "natsUrl",                cfg.natsUrl);
        cfg.natsAuthToken          = getString(obj, "natsAuthToken",          cfg.natsAuthToken);
        cfg.natsUsername           = getString(obj, "natsUsername",           cfg.natsUsername);
        cfg.natsPassword           = getString(obj, "natsPassword",           cfg.natsPassword);
        cfg.shutdownTimeoutSeconds = getInt(obj,    "shutdownTimeoutSeconds", cfg.shutdownTimeoutSeconds);
        return cfg;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return fallback;
        int val = el.getAsInt();
        return val > 0 ? val : fallback;
    }

    private static JsonObject parseJson() {
        try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (!el.isJsonObject()) {
                throw new RuntimeException(
                        "[NatsConfig] Config is not a valid JSON object. Fix or delete: " + CONFIG_PATH);
            }
            return el.getAsJsonObject();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "[NatsConfig] Config cannot be parsed. Fix or delete: " + CONFIG_PATH
                    + "\n  Cause: " + e.getMessage(), e);
        }
    }

    private static void writeJson(JsonObject obj) {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (Exception e) {
            throw new RuntimeException("[NatsConfig] Failed to write config: " + e.getMessage(), e);
        }
    }
}

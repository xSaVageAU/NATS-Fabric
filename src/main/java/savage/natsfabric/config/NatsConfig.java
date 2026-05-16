package savage.natsfabric.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Configuration data for the NATS connection.
 *
 * <p>When adding a new config field:</p>
 * <ol>
 *   <li>Add the Java field with its default value below.</li>
 *   <li>Add a matching {@link ConfigField} entry to {@link #CONFIG_FIELDS}.</li>
 *   <li>Add a mapping line in {@link #fromJson}.</li>
 * </ol>
 * The loading machinery in {@link NatsConfigLoader} does not need to change.
 */
public class NatsConfig {

    // --- Fields ---
    public String serverName             = "server-1";
    public String natsUrl                = "nats://127.0.0.1:4222";
    public String natsAuthToken          = "";
    public String natsUsername           = "";
    public String natsPassword           = "";
    public int    shutdownTimeoutSeconds = 10;

    // --- Field registry ---
    // Add an entry here (in order) when adding a new field above.
    static final List<ConfigField> CONFIG_FIELDS = List.of(
            new ConfigField("serverName",             "server-1"),
            new ConfigField("natsUrl",                "nats://127.0.0.1:4222"),
            new ConfigField("natsAuthToken",          ""),
            new ConfigField("natsUsername",           ""),
            new ConfigField("natsPassword",           ""),
            new ConfigField("shutdownTimeoutSeconds", "10")
    );

    record ConfigField(String key, String defaultValue) {}

    // --- Loader delegate ---

    /** Loads the config from disk. See {@link NatsConfigLoader} for the full behaviour. */
    public static NatsConfig load() {
        return NatsConfigLoader.load();
    }

    // --- JSON mapping ---
    // Add a mapping line here when adding a new field above.
    static NatsConfig fromJson(JsonObject obj) {
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
}

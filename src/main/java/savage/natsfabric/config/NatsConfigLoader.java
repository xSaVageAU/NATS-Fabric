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
 * Handles reading, validating, and writing the NATS config file.
 * This class contains the loading machinery and does not need to change when new config fields are added.
 *
 * @see NatsConfig for field definitions and the steps to add a new field.
 */
class NatsConfigLoader {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("nats-fabric.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads the config from disk. Creates a default file on first run.
     * Inserts missing fields in their correct position on version upgrades.
     * Throws on an unreadable or malformed file — does not silently fall back to defaults.
     */
    static NatsConfig load() {
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

        // Warn about unknown keys — likely a typo or a removed field
        Set<String> knownKeys = NatsConfig.CONFIG_FIELDS.stream()
                .map(NatsConfig.ConfigField::key)
                .collect(Collectors.toSet());
        existing.keySet().stream()
                .filter(k -> !knownKeys.contains(k))
                .forEach(k -> NATSFabric.LOGGER.warn("[NatsConfig] Unknown field '{}' in config — possible typo?", k));

        // Detect missing fields (version upgrade added new options)
        List<String> missing = NatsConfig.CONFIG_FIELDS.stream()
                .filter(f -> !existing.has(f.key()))
                .map(NatsConfig.ConfigField::key)
                .toList();

        JsonObject merged = buildJson(existing);

        if (!missing.isEmpty()) {
            NATSFabric.LOGGER.warn("[NatsConfig] New config fields detected, inserting defaults: {}", missing);
            writeJson(merged);
        }

        return NatsConfig.fromJson(merged);
    }

    /**
     * Builds a JsonObject in CONFIG_FIELDS order.
     * Copies values from {@code existing} where present; uses field defaults otherwise.
     * Pass {@code null} to generate a full-defaults object.
     */
    private static JsonObject buildJson(JsonObject existing) {
        JsonObject out = new JsonObject();
        for (NatsConfig.ConfigField f : NatsConfig.CONFIG_FIELDS) {
            if (existing != null && existing.has(f.key())) {
                out.add(f.key(), existing.get(f.key()));
            } else {
                out.addProperty(f.key(), f.defaultValue());
            }
        }
        return out;
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

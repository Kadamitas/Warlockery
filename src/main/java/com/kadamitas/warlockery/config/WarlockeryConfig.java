package com.kadamitas.warlockery.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WarlockeryConfig {
    private static final String FILE_NAME = "warlockery.json";
    private static final String WORLD_EVENTS = "worldEvents";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(WarlockeryConfig.class);
    private static final Settings DEFAULTS = new Settings(
        true,
        200,
        true,
        2_400,
        0.1D,
        true,
        1_200,
        1.0D / 14.0D,
        true,
        true,
        1.0D
    );

    private static volatile Settings settings = DEFAULTS;

    private WarlockeryConfig() {
    }

    public static void initialize() {
        reload();
    }

    public static synchronized void reload() {
        final Path path = path();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                settings = DEFAULTS;
                write(path, settings);
                return;
            }

            final JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            settings = decode(root);
        } catch (IOException | RuntimeException exception) {
            settings = DEFAULTS;
            LOGGER.warn("Could not load {}; using defaults", path, exception);
        }
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static boolean armPillagers() {
        return settings.armPillagers();
    }

    public static int pillagerScanInterval() {
        return settings.pillagerScanInterval();
    }

    public static boolean hobgoblinEnclaves() {
        return settings.hobgoblinEnclaves();
    }

    public static int hobgoblinEnclaveInterval() {
        return settings.hobgoblinEnclaveInterval();
    }

    public static double hobgoblinEnclaveChance() {
        return settings.hobgoblinEnclaveChance();
    }

    public static boolean silverHunts() {
        return settings.silverHunts();
    }

    public static int silverHuntInterval() {
        return settings.silverHuntInterval();
    }

    public static double silverHuntChance() {
        return settings.silverHuntChance();
    }

    public static boolean settlementFortifications() {
        return settings.settlementFortifications();
    }

    public static boolean villageAssaults() {
        return settings.villageAssaults();
    }

    public static double villageAssaultFrequency() {
        return settings.villageAssaultFrequency();
    }

    private static Settings decode(final JsonObject root) {
        if (root == null) {
            return DEFAULTS;
        }
        final JsonObject worldEvents = object(root, WORLD_EVENTS);
        return new Settings(
            booleanValue(worldEvents, "armPillagersAgainstWerewolves", DEFAULTS.armPillagers()),
            rangedInt(worldEvents, "pillagerScanIntervalTicks", DEFAULTS.pillagerScanInterval(), 20),
            booleanValue(worldEvents, "enableHobgoblinEnclaves", DEFAULTS.hobgoblinEnclaves()),
            rangedInt(worldEvents, "hobgoblinEnclaveAttemptIntervalTicks", DEFAULTS.hobgoblinEnclaveInterval(), 20),
            probability(worldEvents, "hobgoblinEnclaveChance", DEFAULTS.hobgoblinEnclaveChance()),
            booleanValue(worldEvents, "enableSilverHunts", DEFAULTS.silverHunts()),
            rangedInt(worldEvents, "silverHuntAttemptIntervalTicks", DEFAULTS.silverHuntInterval(), 20),
            probability(worldEvents, "silverHuntChance", DEFAULTS.silverHuntChance()),
            booleanValue(worldEvents, "enableSettlementFortifications", DEFAULTS.settlementFortifications()),
            booleanValue(worldEvents, "enableVillageAssaults", DEFAULTS.villageAssaults()),
            rangedDouble(
                worldEvents,
                "villageAssaultDelayMultiplier",
                DEFAULTS.villageAssaultFrequency(),
                0.25D,
                16.0D
            )
        );
    }

    private static JsonObject object(final JsonObject parent, final String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static boolean booleanValue(final JsonObject object, final String key, final boolean fallback) {
        if (!object.has(key) || !(object.get(key) instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            return fallback;
        }
        return primitive.getAsBoolean();
    }

    private static int rangedInt(final JsonObject object, final String key, final int fallback, final int minimum) {
        if (!object.has(key) || !(object.get(key) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return fallback;
        }
        try {
            return Math.max(minimum, primitive.getAsInt());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double probability(final JsonObject object, final String key, final double fallback) {
        if (!object.has(key) || !(object.get(key) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return fallback;
        }
        try {
            final double value = primitive.getAsDouble();
            return Double.isFinite(value) ? Math.clamp(value, 0.0D, 1.0D) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double rangedDouble(
        final JsonObject object,
        final String key,
        final double fallback,
        final double minimum,
        final double maximum
    ) {
        if (!object.has(key) || !(object.get(key) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return fallback;
        }
        try {
            final double value = primitive.getAsDouble();
            return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static void write(final Path path, final Settings value) throws IOException {
        final JsonObject worldEvents = new JsonObject();
        worldEvents.addProperty("armPillagersAgainstWerewolves", value.armPillagers());
        worldEvents.addProperty("pillagerScanIntervalTicks", value.pillagerScanInterval());
        worldEvents.addProperty("enableHobgoblinEnclaves", value.hobgoblinEnclaves());
        worldEvents.addProperty("hobgoblinEnclaveAttemptIntervalTicks", value.hobgoblinEnclaveInterval());
        worldEvents.addProperty("hobgoblinEnclaveChance", value.hobgoblinEnclaveChance());
        worldEvents.addProperty("enableSilverHunts", value.silverHunts());
        worldEvents.addProperty("silverHuntAttemptIntervalTicks", value.silverHuntInterval());
        worldEvents.addProperty("silverHuntChance", value.silverHuntChance());
        worldEvents.addProperty("enableSettlementFortifications", value.settlementFortifications());
        worldEvents.addProperty("enableVillageAssaults", value.villageAssaults());
        worldEvents.addProperty("villageAssaultDelayMultiplier", value.villageAssaultFrequency());

        final JsonObject root = new JsonObject();
        root.add(WORLD_EVENTS, worldEvents);
        Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private record Settings(
        boolean armPillagers,
        int pillagerScanInterval,
        boolean hobgoblinEnclaves,
        int hobgoblinEnclaveInterval,
        double hobgoblinEnclaveChance,
        boolean silverHunts,
        int silverHuntInterval,
        double silverHuntChance,
        boolean settlementFortifications,
        boolean villageAssaults,
        double villageAssaultFrequency
    ) {
    }
}

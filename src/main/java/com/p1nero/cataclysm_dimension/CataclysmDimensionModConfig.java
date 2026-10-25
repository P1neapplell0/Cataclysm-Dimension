package com.p1nero.cataclysm_dimension;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class CataclysmDimensionModConfig {
    public static volatile boolean ENABLE_TELEPORT_EYE = true;
    public static volatile boolean KEEP_STRUCTURES_IN_ORIGINAL_DIMENSIONS = false;
    public static volatile boolean RANDOM_SPREAD_IN_DIMENSION = false;
    public static volatile boolean RESET_DIMENSION_IF_NO_PLAYER = false;
    public static volatile boolean SLOW_FALL_WHEN_ENTER_DIMENSIONS = true;
    public static volatile boolean DISABLE_RESPAWN = false;
    public static volatile boolean CONSUME_TELEPORT_EYE = false;
    public static volatile boolean ALLOW_USE_EYE_IN_DIMENSION = false;
    public static volatile boolean RESET_STRUCTURE_ON_REENTRY = true;
    public static volatile boolean ENABLE_RETURN_RIFT = false;
    public static volatile boolean RESET_STRUCTURE_REQUIRES_BOSS_KILL = false;
    public static volatile boolean PREGENERATE_DIMENSIONS = false;
    public static final String JSON = CataclysmDimensionMod.MOD_ID + ".json";
    public static final Logger LOGGER = LoggerFactory.getLogger("cataclysm_dimension_config");

    public enum Setting {
        TELEPORT_EYE("enable_teleport_eye", true, "live"),
        KEEP_STRUCTURES("keep_structures_in_original_dimensions", false, "worldgen"),
        RANDOM_SPREAD("random_spread_in_dimension", false, "worldgen"),
        EMPTY_RESET("reset_dimension_if_no_player", false, "live"),
        SLOW_FALL("slow_fall_when_enter_dimensions", true, "live"),
        RESPAWN("disable_respawn", false, "restart"),
        CONSUME_EYE("consume_teleport_eye", false, "live"),
        EYE_IN_DIMENSION("allow_use_eye_in_dimension", false, "live"),
        REENTRY_RESET("reset_structure_on_reentry", true, "live"),
        RETURN_RIFT("enable_return_rift", false, "live"),
        BOSS_KILL_RESET("reset_structure_requires_boss_kill", false, "live"),
        PREGENERATE("pregenerate_dimensions", false, "next_load");

        public final String key;
        public final boolean defaultValue;
        public final String effect;

        Setting(String key, boolean defaultValue, String effect) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.effect = effect;
        }

        public String translationKey() {
            return "config.cataclysm_dimension." + key;
        }
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(CataclysmDimensionMod.MOD_ID).resolve(JSON);
    }

    public static synchronized void loadConfig() {
        Path file = configFile();
        JsonObject json = new JsonObject();
        if (Files.exists(file)) {
            try {
                json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception exception) {
                LOGGER.error("Cannot read configuration {}; keeping the file and using defaults", file, exception);
                apply(defaultValues());
                return;
            }
        }
        Map<Setting, Boolean> values = new LinkedHashMap<>();
        boolean changed = !Files.exists(file);
        for (Setting setting : Setting.values()) {
            boolean valid = json.has(setting.key) && json.get(setting.key).isJsonPrimitive()
                    && json.getAsJsonPrimitive(setting.key).isBoolean();
            values.put(setting, valid ? json.get(setting.key).getAsBoolean() : setting.defaultValue);
            if (!valid) {
                json.addProperty(setting.key, setting.defaultValue);
                changed = true;
            }
        }
        apply(values);
        if (changed) {
            try {
                write(json);
            } catch (IOException exception) {
                LOGGER.error("Cannot save configuration {}", file, exception);
            }
        }
    }

    public static Map<Setting, Boolean> defaultValues() {
        Map<Setting, Boolean> values = new LinkedHashMap<>();
        for (Setting setting : Setting.values()) {
            values.put(setting, setting.defaultValue);
        }
        return values;
    }

    public static Map<Setting, Boolean> values() {
        Map<Setting, Boolean> values = new LinkedHashMap<>();
        values.put(Setting.TELEPORT_EYE, ENABLE_TELEPORT_EYE);
        values.put(Setting.KEEP_STRUCTURES, KEEP_STRUCTURES_IN_ORIGINAL_DIMENSIONS);
        values.put(Setting.RANDOM_SPREAD, RANDOM_SPREAD_IN_DIMENSION);
        values.put(Setting.EMPTY_RESET, RESET_DIMENSION_IF_NO_PLAYER);
        values.put(Setting.SLOW_FALL, SLOW_FALL_WHEN_ENTER_DIMENSIONS);
        values.put(Setting.RESPAWN, DISABLE_RESPAWN);
        values.put(Setting.CONSUME_EYE, CONSUME_TELEPORT_EYE);
        values.put(Setting.EYE_IN_DIMENSION, ALLOW_USE_EYE_IN_DIMENSION);
        values.put(Setting.REENTRY_RESET, RESET_STRUCTURE_ON_REENTRY);
        values.put(Setting.RETURN_RIFT, ENABLE_RETURN_RIFT);
        values.put(Setting.BOSS_KILL_RESET, RESET_STRUCTURE_REQUIRES_BOSS_KILL);
        values.put(Setting.PREGENERATE, PREGENERATE_DIMENSIONS);
        return values;
    }

    public static synchronized boolean save(Map<Setting, Boolean> values) {
        try {
            Map<Setting, Boolean> normalized = defaultValues();
            normalized.putAll(values);
            Path file = configFile();
            JsonObject json = Files.exists(file)
                    ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            for (Setting setting : Setting.values()) {
                json.addProperty(setting.key, normalized.get(setting));
            }
            write(json);
            apply(normalized);
            return true;
        } catch (Exception exception) {
            LOGGER.error("Cannot save configuration; leaving runtime values unchanged", exception);
            return false;
        }
    }

    private static void write(JsonObject json) throws IOException {
        Path file = configFile();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "cataclysm-config-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void apply(Map<Setting, Boolean> values) {
        ENABLE_TELEPORT_EYE = values.get(Setting.TELEPORT_EYE);
        KEEP_STRUCTURES_IN_ORIGINAL_DIMENSIONS = values.get(Setting.KEEP_STRUCTURES);
        RANDOM_SPREAD_IN_DIMENSION = values.get(Setting.RANDOM_SPREAD);
        RESET_DIMENSION_IF_NO_PLAYER = values.get(Setting.EMPTY_RESET);
        SLOW_FALL_WHEN_ENTER_DIMENSIONS = values.get(Setting.SLOW_FALL);
        DISABLE_RESPAWN = values.get(Setting.RESPAWN);
        CONSUME_TELEPORT_EYE = values.get(Setting.CONSUME_EYE);
        ALLOW_USE_EYE_IN_DIMENSION = values.get(Setting.EYE_IN_DIMENSION);
        RESET_STRUCTURE_ON_REENTRY = values.get(Setting.REENTRY_RESET);
        ENABLE_RETURN_RIFT = values.get(Setting.RETURN_RIFT);
        RESET_STRUCTURE_REQUIRES_BOSS_KILL = values.get(Setting.BOSS_KILL_RESET);
        PREGENERATE_DIMENSIONS = values.get(Setting.PREGENERATE);
    }
}

package com.example.geminitp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player AI personality configuration.
 * Each player can have their own Bernard personality mode.
 * Config file stores a map of playerName -> mode.
 */
public class PersonalityConfig {

    public enum PersonalityMode {
        KIND,       // Helpful and friendly assistant
        DICKHEAD,   // Cooperative but dismissive/rude
        YANDERE     // Obsessively attached, possessive but comedic
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    // Maps player name (lowercase) -> personality mode
    private final Map<String, PersonalityMode> playerModes = new ConcurrentHashMap<>();

    public PersonalityConfig(File configFile) {
        this.configFile = configFile;
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, PersonalityMode>>(){}.getType();
            Map<String, PersonalityMode> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                playerModes.putAll(loaded);
            }
        } catch (IOException e) {
            GeminiTpMod.LOGGER.error("Failed to load personality config", e);
        }
    }

    public void saveConfig() {
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(playerModes, writer);
            }
        } catch (IOException e) {
            GeminiTpMod.LOGGER.error("Failed to save personality config", e);
        }
    }

    public PersonalityMode getMode(String playerName) {
        return playerModes.getOrDefault(playerName.toLowerCase(), PersonalityMode.KIND);
    }

    public void setMode(String playerName, PersonalityMode mode) {
        playerModes.put(playerName.toLowerCase(), mode);
        saveConfig();
    }

    public boolean isDickheadMode(String playerName) {
        return getMode(playerName) == PersonalityMode.DICKHEAD;
    }

    public boolean isKindMode(String playerName) {
        return getMode(playerName) == PersonalityMode.KIND;
    }

    public boolean isYandereMode(String playerName) {
        return getMode(playerName) == PersonalityMode.YANDERE;
    }

    public void toggleMode(String playerName) {
        PersonalityMode current = getMode(playerName);
        PersonalityMode next = switch (current) {
            case KIND -> PersonalityMode.DICKHEAD;
            case DICKHEAD -> PersonalityMode.YANDERE;
            case YANDERE -> PersonalityMode.KIND;
        };
        setMode(playerName, next);
    }

    // Legacy global methods — kept for backward compat but now just check defaults
    /** @deprecated Use getMode(playerName) instead */
    public PersonalityMode getMode() { return PersonalityMode.KIND; }
    /** @deprecated Use isDickheadMode(playerName) instead */
    public boolean isDickheadMode() { return false; }
    /** @deprecated Use isKindMode(playerName) instead */
    public boolean isKindMode() { return true; }
    /** @deprecated Use isYandereMode(playerName) instead */
    public boolean isYandereMode() { return false; }
}

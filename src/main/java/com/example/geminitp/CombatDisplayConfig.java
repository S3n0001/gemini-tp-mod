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
 * Per-player combat HUD display preferences.
 * Persisted to JSON so settings survive restarts.
 */
public class CombatDisplayConfig {

    /**
     * Display modes for the combat timer HUD:
     * ALWAYS       — shows the full combat tag + warning text at all times (default)
     * ALWAYS_CLEAN — shows the combat tag at all times but without the warning text underneath
     * HIDDEN       — never shows the combat HUD
     * PULSE        — shows for 3 seconds, hides for 5 seconds, repeats
     */
    public enum DisplayMode {
        ALWAYS,
        ALWAYS_CLEAN,
        HIDDEN,
        PULSE
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File configFile;
    private final Map<String, PlayerPrefs> playerPrefs = new ConcurrentHashMap<>();

    public CombatDisplayConfig(File configFile) {
        this.configFile = configFile;
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, PlayerPrefs>>(){}.getType();
            Map<String, PlayerPrefs> loaded = GSON.fromJson(reader, type);
            if (loaded != null) playerPrefs.putAll(loaded);
        } catch (IOException e) {
            GeminiTpMod.LOGGER.error("Failed to load combat display config", e);
        }
    }

    public void saveConfig() {
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(playerPrefs, writer);
            }
        } catch (IOException e) {
            GeminiTpMod.LOGGER.error("Failed to save combat display config", e);
        }
    }

    private PlayerPrefs getOrCreate(String playerName) {
        return playerPrefs.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerPrefs());
    }

    public DisplayMode getDisplayMode(String playerName) {
        return getOrCreate(playerName).displayMode;
    }

    public void setDisplayMode(String playerName, DisplayMode mode) {
        getOrCreate(playerName).displayMode = mode;
        saveConfig();
    }

    public boolean isSoundEnabled(String playerName) {
        return getOrCreate(playerName).soundEnabled;
    }

    public void setSoundEnabled(String playerName, boolean enabled) {
        getOrCreate(playerName).soundEnabled = enabled;
        saveConfig();
    }

    public void toggleSound(String playerName) {
        PlayerPrefs prefs = getOrCreate(playerName);
        prefs.soundEnabled = !prefs.soundEnabled;
        saveConfig();
    }

    /** Cycle through display modes: ALWAYS -> ALWAYS_CLEAN -> HIDDEN -> PULSE -> ALWAYS */
    public DisplayMode cycleMode(String playerName) {
        PlayerPrefs prefs = getOrCreate(playerName);
        prefs.displayMode = switch (prefs.displayMode) {
            case ALWAYS -> DisplayMode.ALWAYS_CLEAN;
            case ALWAYS_CLEAN -> DisplayMode.HIDDEN;
            case HIDDEN -> DisplayMode.PULSE;
            case PULSE -> DisplayMode.ALWAYS;
        };
        saveConfig();
        return prefs.displayMode;
    }

    public String getDisplayModeDescription(DisplayMode mode) {
        return switch (mode) {
            case ALWAYS -> "§fAlways§7 — Full combat tag with warning text";
            case ALWAYS_CLEAN -> "§fAlways (Clean)§7 — Combat tag without warning text";
            case HIDDEN -> "§fHidden§7 — Combat HUD disabled";
            case PULSE -> "§fPulse§7 — Shows 3s, hides 5s, repeats";
        };
    }

    public static class PlayerPrefs {
        public DisplayMode displayMode = DisplayMode.ALWAYS;
        public boolean soundEnabled = true;
    }
}

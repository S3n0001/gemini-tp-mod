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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the "beg bonus" easter egg — players who meow/bark/beg enough
 * to Bernard get +1 to all TP limits per hour for 3 hours.
 * After the 3 hours expire, they must wait for the bonus to fully end
 * before they can earn it again (no stacking).
 */
public class BegBonus {

    private static final long BONUS_DURATION_MS = 3 * 60 * 60 * 1000L; // 3 hours
    private static final int BONUS_AMOUNT = 1; // +1 to each TP type per hour
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File saveFile;
    // UUID -> bonus state
    private final Map<String, BonusState> bonuses = new ConcurrentHashMap<>();

    public BegBonus(File saveFile) {
        this.saveFile = saveFile;
        load();
    }

    /** Grant the bonus to a player. Returns false if they already have an active/cooling bonus. */
    public boolean grant(UUID playerId) {
        String key = playerId.toString();
        BonusState existing = bonuses.get(key);

        if (existing != null && !existing.isFullyExpired()) {
            return false; // Still active or cooling down
        }

        bonuses.put(key, new BonusState(System.currentTimeMillis()));
        save();
        GeminiTpMod.LOGGER.info("Beg bonus granted to {}", playerId);
        return true;
    }

    /** Check if a player currently has the +1 bonus active. */
    public boolean isActive(UUID playerId) {
        BonusState state = bonuses.get(playerId.toString());
        if (state == null) return false;
        return state.isActive();
    }

    /** Check if a player can earn the bonus again (fully expired). */
    public boolean canEarnAgain(UUID playerId) {
        BonusState state = bonuses.get(playerId.toString());
        if (state == null) return true;
        return state.isFullyExpired();
    }

    /** Get remaining time on the active bonus in ms. 0 if not active. */
    public long getRemainingMs(UUID playerId) {
        BonusState state = bonuses.get(playerId.toString());
        if (state == null || !state.isActive()) return 0;
        return state.getRemainingMs();
    }

    /** Get the bonus amount (how many extra TPs per type). */
    public int getBonusAmount(UUID playerId) {
        return isActive(playerId) ? BONUS_AMOUNT : 0;
    }

    private void load() {
        if (!saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            Type type = new TypeToken<Map<String, BonusState>>(){}.getType();
            Map<String, BonusState> loaded = GSON.fromJson(reader, type);
            if (loaded != null) bonuses.putAll(loaded);
        } catch (IOException e) {
            GeminiTpMod.LOGGER.error("Failed to load beg bonus data", e);
        }
    }

    private void save() {
        try {
            saveFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(saveFile)) {
                GSON.toJson(bonuses, writer);
            }
        } catch (IOException e) {
            GeminiTpMod.LOGGER.error("Failed to save beg bonus data", e);
        }
    }

    public static class BonusState {
        public long grantedAt;

        public BonusState() {} // for Gson

        public BonusState(long grantedAt) {
            this.grantedAt = grantedAt;
        }

        public boolean isActive() {
            return System.currentTimeMillis() - grantedAt < BONUS_DURATION_MS;
        }

        public boolean isFullyExpired() {
            return System.currentTimeMillis() - grantedAt >= BONUS_DURATION_MS;
        }

        public long getRemainingMs() {
            long remaining = BONUS_DURATION_MS - (System.currentTimeMillis() - grantedAt);
            return Math.max(0, remaining);
        }
    }
}

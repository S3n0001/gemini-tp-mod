package com.example.geminitp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
	private static final int MAX_DEATH_TELEPORTS_PER_HOUR = 5;
	private static final int MAX_PLAYER_TELEPORTS_PER_HOUR = 10;
	private static final int MAX_LOCATION_TELEPORTS_PER_HOUR = 15;
	private static final long ONE_HOUR_MS = 60 * 60 * 1000; // 1 hour in milliseconds
	private static final Path SAVE_FILE = Paths.get("config", "gemini-tp-data.json");
	
	private final Map<UUID, PlayerTeleportData> playerData;
	private final Gson gson;
	private BegBonus begBonus; // Optional — set after initialization

	public RateLimiter() {
		this.playerData = new ConcurrentHashMap<>();
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		loadData();
	}

	public void setBegBonus(BegBonus begBonus) {
		this.begBonus = begBonus;
	}

	private int getDeathMax(ServerPlayerEntity player) {
		int bonus = begBonus != null ? begBonus.getBonusAmount(player.getUuid()) : 0;
		return MAX_DEATH_TELEPORTS_PER_HOUR + bonus;
	}

	private int getPlayerMax(ServerPlayerEntity player) {
		int bonus = begBonus != null ? begBonus.getBonusAmount(player.getUuid()) : 0;
		return MAX_PLAYER_TELEPORTS_PER_HOUR + bonus;
	}

	private int getLocationMax(ServerPlayerEntity player) {
		int bonus = begBonus != null ? begBonus.getBonusAmount(player.getUuid()) : 0;
		return MAX_LOCATION_TELEPORTS_PER_HOUR + bonus;
	}
	
	public boolean canDeathTeleport(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		return data.deathTeleportsUsed < getDeathMax(player);
	}
	
	public boolean canPlayerTeleport(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		return data.playerTeleportsUsed < getPlayerMax(player);
	}
	
	public boolean canLocationTeleport(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		return data.locationTeleportsUsed < getLocationMax(player);
	}
	
	public void useDeathTeleport(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		data.addDeathTeleport();
		saveData();
		
		GeminiTpMod.LOGGER.info("Player {} used a death teleport. Remaining: {}", 
			player.getName().getString(), getDeathTeleportsRemaining(player));
	}
	
	public void usePlayerTeleport(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		data.addPlayerTeleport();
		saveData();
		
		GeminiTpMod.LOGGER.info("Player {} used a player teleport. Remaining: {}", 
			player.getName().getString(), getPlayerTeleportsRemaining(player));
	}
	
	public void useLocationTeleport(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		data.addLocationTeleport();
		saveData();
		
		GeminiTpMod.LOGGER.info("Player {} used a location teleport. Remaining: {}", 
			player.getName().getString(), getLocationTeleportsRemaining(player));
	}
	
	public int getDeathTeleportsRemaining(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		return getDeathMax(player) - data.deathTeleportsUsed;
	}
	
	public int getPlayerTeleportsRemaining(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		return getPlayerMax(player) - data.playerTeleportsUsed;
	}
	
	public int getLocationTeleportsRemaining(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		PlayerTeleportData data = playerData.computeIfAbsent(playerId, k -> new PlayerTeleportData());
		
		data.checkAndResetIfNeeded();
		return getLocationMax(player) - data.locationTeleportsUsed;
	}
	
	private void loadData() {
		try {
			if (!Files.exists(SAVE_FILE)) {
				// Create config directory if it doesn't exist
				Files.createDirectories(SAVE_FILE.getParent());
				return;
			}
			
			String json = Files.readString(SAVE_FILE);
			Type type = new TypeToken<Map<String, PlayerTeleportData>>(){}.getType();
			Map<String, PlayerTeleportData> loadedData = gson.fromJson(json, type);
			
			if (loadedData != null) {
				// Convert string UUIDs back to UUID objects
				for (Map.Entry<String, PlayerTeleportData> entry : loadedData.entrySet()) {
					playerData.put(UUID.fromString(entry.getKey()), entry.getValue());
				}
			}
			
			GeminiTpMod.LOGGER.info("Loaded teleport data for {} players", playerData.size());
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Failed to load teleport data", e);
		}
	}
	
	private void saveData() {
		try {
			// Convert UUID keys to strings for JSON serialization
			Map<String, PlayerTeleportData> saveData = new HashMap<>();
			for (Map.Entry<UUID, PlayerTeleportData> entry : playerData.entrySet()) {
				saveData.put(entry.getKey().toString(), entry.getValue());
			}
			
			String json = gson.toJson(saveData);
			Files.createDirectories(SAVE_FILE.getParent());
			Files.writeString(SAVE_FILE, json);
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Failed to save teleport data", e);
		}
	}
	
	public void shutdown() {
		saveData();
	}
	
	private static class PlayerTeleportData {
		private long firstTeleportTime = 0;    // When they first used a teleport
		private long lastResetTime = 0;        // When teleports were last reset
		private int deathTeleportsUsed = 0;    // Death teleports used in current period
		private int playerTeleportsUsed = 0;   // Player teleports used in current period
		private int locationTeleportsUsed = 0; // Location teleports used in current period
		
		public PlayerTeleportData() {
			// Default constructor for JSON deserialization
		}
		
		public void addDeathTeleport() {
			long currentTime = System.currentTimeMillis();
			
			// If this is their first teleport ever, set the baseline
			if (firstTeleportTime == 0) {
				firstTeleportTime = currentTime;
				lastResetTime = currentTime;
			}
			
			deathTeleportsUsed++;
		}
		
		public void addPlayerTeleport() {
			long currentTime = System.currentTimeMillis();
			
			// If this is their first teleport ever, set the baseline
			if (firstTeleportTime == 0) {
				firstTeleportTime = currentTime;
				lastResetTime = currentTime;
			}
			
			playerTeleportsUsed++;
		}
		
		public void addLocationTeleport() {
			long currentTime = System.currentTimeMillis();
			
			// If this is their first teleport ever, set the baseline
			if (firstTeleportTime == 0) {
				firstTeleportTime = currentTime;
				lastResetTime = currentTime;
			}
			
			locationTeleportsUsed++;
		}
		
		public void checkAndResetIfNeeded() {
			long currentTime = System.currentTimeMillis();
			
			// If no teleports used yet, nothing to reset
			if (firstTeleportTime == 0) {
				return;
			}
			
			// Check if an hour has passed since last reset
			if (currentTime - lastResetTime >= ONE_HOUR_MS) {
				// Calculate how many complete hours have passed since last reset
				long hoursElapsed = (currentTime - lastResetTime) / ONE_HOUR_MS;
				
				// Reset all types of teleports and update last reset time
				deathTeleportsUsed = 0;
				playerTeleportsUsed = 0;
				locationTeleportsUsed = 0;
				lastResetTime += (hoursElapsed * ONE_HOUR_MS);
				
				GeminiTpMod.LOGGER.debug("Reset teleports for player. Hours elapsed: {}", hoursElapsed);
			}
		}
	}
}
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

public class LocationSaver {
	private static final Path SAVE_FILE = Paths.get("config", "gemini-saved-locations.json");
	
	private final Map<UUID, SavedLocation> playerLocations;
	private final Gson gson;
	
	public LocationSaver() {
		this.playerLocations = new ConcurrentHashMap<>();
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		loadData();
	}
	
	public void saveLocation(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		String dimension = player.getCommandSource().getWorld().getRegistryKey().getValue().toString();
		
		SavedLocation location = new SavedLocation(x, y, z, dimension, System.currentTimeMillis());
		playerLocations.put(playerId, location);
		
		saveData();
		
		GeminiTpMod.LOGGER.info("Saved location for player {} at {}, {}, {} in {}", 
			player.getName().getString(), x, y, z, dimension);
	}
	
	public SavedLocation getSavedLocation(ServerPlayerEntity player) {
		return playerLocations.get(player.getUuid());
	}
	
	public boolean hasSavedLocation(ServerPlayerEntity player) {
		return playerLocations.containsKey(player.getUuid());
	}
	
	private void loadData() {
		try {
			if (!Files.exists(SAVE_FILE)) {
				// Create config directory if it doesn't exist
				Files.createDirectories(SAVE_FILE.getParent());
				return;
			}
			
			String json = Files.readString(SAVE_FILE);
			Type type = new TypeToken<Map<String, SavedLocation>>(){}.getType();
			Map<String, SavedLocation> loadedData = gson.fromJson(json, type);
			
			if (loadedData != null) {
				// Convert string UUIDs back to UUID objects
				for (Map.Entry<String, SavedLocation> entry : loadedData.entrySet()) {
					playerLocations.put(UUID.fromString(entry.getKey()), entry.getValue());
				}
			}
			
			GeminiTpMod.LOGGER.info("Loaded saved locations for {} players", playerLocations.size());
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Failed to load saved locations", e);
		}
	}
	
	private void saveData() {
		try {
			// Convert UUID keys to strings for JSON serialization
			Map<String, SavedLocation> saveData = new HashMap<>();
			for (Map.Entry<UUID, SavedLocation> entry : playerLocations.entrySet()) {
				saveData.put(entry.getKey().toString(), entry.getValue());
			}
			
			String json = gson.toJson(saveData);
			Files.createDirectories(SAVE_FILE.getParent());
			Files.writeString(SAVE_FILE, json);
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Failed to save location data", e);
		}
	}
	
	public void shutdown() {
		saveData();
	}
	
	public static class SavedLocation {
		public final double x;
		public final double y;
		public final double z;
		public final String dimension;
		public final long savedTimestamp;
		
		public SavedLocation(double x, double y, double z, String dimension, long savedTimestamp) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
			this.savedTimestamp = savedTimestamp;
		}
	}
}

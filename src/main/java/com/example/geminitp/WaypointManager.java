package com.example.geminitp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaypointManager {
	private static final Path SAVE_FILE = Paths.get("config", "gemini-waypoints.json");
	private static final int MAX_WAYPOINTS = 30;
	
	private final Map<UUID, Map<String, Waypoint>> playerWaypoints;
	private final Gson gson;
	
	public WaypointManager() {
		this.playerWaypoints = new ConcurrentHashMap<>();
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		loadData();
	}
	
	public boolean createWaypoint(ServerPlayerEntity player, String name) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
		
		if (waypoints.size() >= MAX_WAYPOINTS) {
			return false; // Max waypoints reached
		}
		
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		String dimension = player.getCommandSource().getWorld().getRegistryKey().getValue().toString();
		
		Waypoint waypoint = new Waypoint(name, x, y, z, dimension, System.currentTimeMillis());
		waypoints.put(name.toLowerCase(), waypoint);
		
		saveData();
		
		GeminiTpMod.LOGGER.info("Created waypoint '{}' for player {} at {}, {}, {} in {}", 
			name, player.getName().getString(), x, y, z, dimension);
		
		return true;
	}
	
	public boolean renameWaypoint(ServerPlayerEntity player, String oldName, String newName) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.get(playerId);
		
		if (waypoints == null) {
			return false;
		}
		
		Waypoint waypoint = waypoints.remove(oldName.toLowerCase());
		if (waypoint == null) {
			return false;
		}
		
		// Check if new name already exists
		if (waypoints.containsKey(newName.toLowerCase())) {
			// Restore old waypoint
			waypoints.put(oldName.toLowerCase(), waypoint);
			return false;
		}
		
		// Create new waypoint with updated name
		Waypoint renamedWaypoint = new Waypoint(newName, waypoint.x, waypoint.y, waypoint.z, 
			waypoint.dimension, waypoint.savedTimestamp);
		waypoints.put(newName.toLowerCase(), renamedWaypoint);
		
		saveData();
		
		GeminiTpMod.LOGGER.info("Renamed waypoint '{}' to '{}' for player {}", 
			oldName, newName, player.getName().getString());
		
		return true;
	}
	
	public boolean deleteWaypoint(ServerPlayerEntity player, String name) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.get(playerId);
		
		if (waypoints == null) {
			return false;
		}
		
		Waypoint removed = waypoints.remove(name.toLowerCase());
		if (removed != null) {
			saveData();
			GeminiTpMod.LOGGER.info("Deleted waypoint '{}' for player {}", 
				name, player.getName().getString());
			return true;
		}
		
		return false;
	}
	
	public Waypoint getWaypoint(ServerPlayerEntity player, String name) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.get(playerId);
		
		if (waypoints == null) {
			return null;
		}
		
		return waypoints.get(name.toLowerCase());
	}
	
	public Map<String, Waypoint> getAllWaypoints(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.get(playerId);
		
		if (waypoints == null) {
			return new HashMap<>();
		}
		
		return new HashMap<>(waypoints);
	}
	
	public int getWaypointCount(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.get(playerId);
		return waypoints == null ? 0 : waypoints.size();
	}
	
	public boolean hasWaypoint(ServerPlayerEntity player, String name) {
		UUID playerId = player.getUuid();
		Map<String, Waypoint> waypoints = playerWaypoints.get(playerId);
		return waypoints != null && waypoints.containsKey(name.toLowerCase());
	}
	
	public boolean hasAnyWaypoints(ServerPlayerEntity player) {
		return getWaypointCount(player) > 0;
	}
	
	public int getRemainingSlots(ServerPlayerEntity player) {
		return MAX_WAYPOINTS - getWaypointCount(player);
	}
	
	private void loadData() {
		try {
			if (!Files.exists(SAVE_FILE)) {
				Files.createDirectories(SAVE_FILE.getParent());
				return;
			}
			
			String json = Files.readString(SAVE_FILE);
			Type type = new TypeToken<Map<String, Map<String, Waypoint>>>(){}.getType();
			Map<String, Map<String, Waypoint>> loadedData = gson.fromJson(json, type);
			
			if (loadedData != null) {
				for (Map.Entry<String, Map<String, Waypoint>> entry : loadedData.entrySet()) {
					playerWaypoints.put(UUID.fromString(entry.getKey()), new ConcurrentHashMap<>(entry.getValue()));
				}
			}
			
			GeminiTpMod.LOGGER.info("Loaded waypoints for {} players", playerWaypoints.size());
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Failed to load waypoints", e);
		}
	}
	
	private void saveData() {
		try {
			Map<String, Map<String, Waypoint>> saveData = new HashMap<>();
			for (Map.Entry<UUID, Map<String, Waypoint>> entry : playerWaypoints.entrySet()) {
				saveData.put(entry.getKey().toString(), entry.getValue());
			}
			
			String json = gson.toJson(saveData);
			Files.createDirectories(SAVE_FILE.getParent());
			Files.writeString(SAVE_FILE, json);
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Failed to save waypoint data", e);
		}
	}
	
	public void shutdown() {
		saveData();
	}
	
	public static class Waypoint {
		public final String name;
		public final double x;
		public final double y;
		public final double z;
		public final String dimension;
		public final long savedTimestamp;
		
		public Waypoint(String name, double x, double y, double z, String dimension, long savedTimestamp) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
			this.savedTimestamp = savedTimestamp;
		}
		
		@Override
		public String toString() {
			return String.format("%s: %.2f, %.2f, %.2f in %s", name, x, y, z, dimension);
		}
	}
}

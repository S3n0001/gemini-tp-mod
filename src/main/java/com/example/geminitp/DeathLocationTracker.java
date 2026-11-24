package com.example.geminitp;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeathLocationTracker {
	private final Map<UUID, List<DeathLocation>> deathHistory = new ConcurrentHashMap<>();
	private final Map<UUID, DeathLocation> currentDeathLocation = new ConcurrentHashMap<>();
	
	public void recordDeath(ServerPlayerEntity player) {
		Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
		String dimension = player.getCommandSource().getWorld().getRegistryKey().getValue().toString();
		long timestamp = System.currentTimeMillis();
		
		DeathLocation location = new DeathLocation(
			pos.x, pos.y, pos.z, dimension, false, timestamp
		);
		
		// Add to history
		List<DeathLocation> history = deathHistory.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
		history.add(location);
		
		// Set as current death location
		currentDeathLocation.put(player.getUuid(), location);
		
		GeminiTpMod.LOGGER.info("Recorded death #{} for {}: {}", 
			history.size(), player.getName().getString(), location);
	}
	
	public void markDeathLocationUsed(ServerPlayerEntity player) {
		DeathLocation location = currentDeathLocation.get(player.getUuid());
		if (location != null) {
			DeathLocation usedLocation = new DeathLocation(
				location.x, location.y, location.z, location.dimension, true, location.timestamp
			);
			currentDeathLocation.put(player.getUuid(), usedLocation);
			
			// Update in history as well
			List<DeathLocation> history = deathHistory.get(player.getUuid());
			if (history != null && !history.isEmpty()) {
				// Replace the last entry with the used version
				history.set(history.size() - 1, usedLocation);
			}
		}
	}
	
	public boolean isDeathLocationUsed(ServerPlayerEntity player) {
		DeathLocation location = currentDeathLocation.get(player.getUuid());
		return location != null && location.used;
	}
	
	public DeathLocation getLastDeathLocation(ServerPlayerEntity player) {
		return currentDeathLocation.get(player.getUuid());
	}
	
	public List<DeathLocation> getDeathHistory(ServerPlayerEntity player) {
		List<DeathLocation> history = deathHistory.get(player.getUuid());
		return history != null ? new ArrayList<>(history) : new ArrayList<>();
	}
	
	public List<DeathLocation> getDeathHistory(UUID playerUuid) {
		List<DeathLocation> history = deathHistory.get(playerUuid);
		return history != null ? new ArrayList<>(history) : new ArrayList<>();
	}
	
	public void clearDeathLocation(ServerPlayerEntity player) {
		currentDeathLocation.remove(player.getUuid());
	}
	
	public Map<UUID, DeathLocation> getAllCurrentDeathLocations() {
		return new HashMap<>(currentDeathLocation);
	}
	
	public Map<UUID, List<DeathLocation>> getAllDeathHistory() {
		return new HashMap<>(deathHistory);
	}
	
	public int getTotalDeaths(ServerPlayerEntity player) {
		List<DeathLocation> history = deathHistory.get(player.getUuid());
		return history != null ? history.size() : 0;
	}
	
	public static class DeathLocation {
		public final double x;
		public final double y;
		public final double z;
		public final String dimension;
		public final boolean used;
		public final long timestamp;
		
		public DeathLocation(double x, double y, double z, String dimension, boolean used, long timestamp) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
			this.used = used;
			this.timestamp = timestamp;
		}
		
		public String getFormattedTimestamp() {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
				.withZone(ZoneId.systemDefault());
			return formatter.format(Instant.ofEpochMilli(timestamp));
		}
		
		@Override
		public String toString() {
			return String.format("%.2f, %.2f, %.2f in %s%s", x, y, z, dimension, used ? " (used)" : "");
		}
	}
}

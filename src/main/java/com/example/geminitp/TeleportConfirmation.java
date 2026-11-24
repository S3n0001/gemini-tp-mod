package com.example.geminitp;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportConfirmation {
	private final Map<UUID, PendingTeleport> pendingTeleports;
	
	public TeleportConfirmation() {
		this.pendingTeleports = new ConcurrentHashMap<>();
	}
	
	public void createPendingDeathTeleport(ServerPlayerEntity player, double x, double y, double z, String dimension) {
		PendingTeleport pending = new PendingTeleport(x, y, z, dimension, System.currentTimeMillis(), TeleportType.DEATH);
		pendingTeleports.put(player.getUuid(), pending);
	}
	
	public void createPendingPlayerTeleport(ServerPlayerEntity player, ServerPlayerEntity targetPlayer) {
		double x = targetPlayer.getX();
		double y = targetPlayer.getY();
		double z = targetPlayer.getZ();
		String dimension = targetPlayer.getCommandSource().getWorld().getRegistryKey().getValue().toString();
		
		PendingTeleport pending = new PendingTeleport(x, y, z, dimension, System.currentTimeMillis(), TeleportType.PLAYER, targetPlayer.getName().getString());
		pendingTeleports.put(player.getUuid(), pending);
	}
	
	public void createPendingLocationTeleport(ServerPlayerEntity player, double x, double y, double z, String dimension) {
		PendingTeleport pending = new PendingTeleport(x, y, z, dimension, System.currentTimeMillis(), TeleportType.LOCATION);
		pendingTeleports.put(player.getUuid(), pending);
	}
	
	public PendingTeleport getPendingTeleport(ServerPlayerEntity player) {
		return pendingTeleports.get(player.getUuid());
	}
	
	public void removePendingTeleport(ServerPlayerEntity player) {
		pendingTeleports.remove(player.getUuid());
	}
	
	public boolean hasPendingTeleport(ServerPlayerEntity player) {
		PendingTeleport pending = pendingTeleports.get(player.getUuid());
		if (pending == null) {
			return false;
		}
		
		// Expire confirmations after 30 seconds
		if (System.currentTimeMillis() - pending.timestamp > 30000) {
			pendingTeleports.remove(player.getUuid());
			return false;
		}
		
		return true;
	}
	
	public enum TeleportType {
		DEATH, PLAYER, LOCATION
	}
	
	public static class PendingTeleport {
		public final double x;
		public final double y;
		public final double z;
		public final String dimension;
		public final long timestamp;
		public final TeleportType type;
		public final String targetPlayerName; // For player teleports
		
		public PendingTeleport(double x, double y, double z, String dimension, long timestamp, TeleportType type) {
			this(x, y, z, dimension, timestamp, type, null);
		}
		
		public PendingTeleport(double x, double y, double z, String dimension, long timestamp, TeleportType type, String targetPlayerName) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
			this.timestamp = timestamp;
			this.type = type;
			this.targetPlayerName = targetPlayerName;
		}
	}
}

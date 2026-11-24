package com.example.geminitp;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportRequest {
	private final Map<UUID, Map<UUID, PendingRequest>> pendingRequests; // targetId -> (requesterId -> request)
	private final Map<UUID, PendingConfirmation> pendingConfirmations; // requesterId -> confirmation
	
	public TeleportRequest() {
		this.pendingRequests = new ConcurrentHashMap<>();
		this.pendingConfirmations = new ConcurrentHashMap<>();
	}
	
	public void createRequest(ServerPlayerEntity requester, ServerPlayerEntity target) {
		PendingRequest request = new PendingRequest(
			requester.getUuid(), 
			requester.getName().getString(),
			target.getUuid(),
			target.getName().getString(),
			System.currentTimeMillis()
		);
		
		pendingRequests.computeIfAbsent(target.getUuid(), k -> new ConcurrentHashMap<>())
			.put(requester.getUuid(), request);
	}
	
	public Map<UUID, PendingRequest> getPendingRequests(ServerPlayerEntity targetPlayer) {
		UUID targetId = targetPlayer.getUuid();
		Map<UUID, PendingRequest> requests = pendingRequests.get(targetId);
		
		if (requests == null) {
			return new ConcurrentHashMap<>();
		}
		
		// Remove expired requests (15 minutes)
		long currentTime = System.currentTimeMillis();
		requests.entrySet().removeIf(entry -> 
			currentTime - entry.getValue().timestamp > 900000
		);
		
		if (requests.isEmpty()) {
			pendingRequests.remove(targetId);
		}
		
		return requests;
	}
	
	public PendingRequest getOldestPendingRequest(ServerPlayerEntity targetPlayer) {
		Map<UUID, PendingRequest> requests = getPendingRequests(targetPlayer);
		if (requests.isEmpty()) {
			return null;
		}
		
		return requests.values().stream()
			.min((r1, r2) -> Long.compare(r1.timestamp, r2.timestamp))
			.orElse(null);
	}
	
	public PendingRequest removePendingRequest(ServerPlayerEntity targetPlayer, UUID requesterId) {
		Map<UUID, PendingRequest> requests = pendingRequests.get(targetPlayer.getUuid());
		if (requests == null) {
			return null;
		}
		
		PendingRequest removed = requests.remove(requesterId);
		if (requests.isEmpty()) {
			pendingRequests.remove(targetPlayer.getUuid());
		}
		
		return removed;
	}
	
	public boolean hasPendingRequests(ServerPlayerEntity targetPlayer) {
		return !getPendingRequests(targetPlayer).isEmpty();
	}
	
	public int getPendingRequestCount(ServerPlayerEntity targetPlayer) {
		return getPendingRequests(targetPlayer).size();
	}
	
	public void createConfirmation(ServerPlayerEntity requester, ServerPlayerEntity target) {
		PendingConfirmation confirmation = new PendingConfirmation(
			target.getUuid(),
			target.getName().getString(),
			System.currentTimeMillis()
		);
		pendingConfirmations.put(requester.getUuid(), confirmation);
	}
	
	public PendingConfirmation getPendingConfirmation(ServerPlayerEntity requester) {
		PendingConfirmation confirmation = pendingConfirmations.get(requester.getUuid());
		
		if (confirmation == null) {
			return null;
		}
		
		// Remove if expired (2 minutes)
		long currentTime = System.currentTimeMillis();
		if (currentTime - confirmation.timestamp > 120000) {
			pendingConfirmations.remove(requester.getUuid());
			return null;
		}
		
		return confirmation;
	}
	
	public void removePendingConfirmation(ServerPlayerEntity requester) {
		pendingConfirmations.remove(requester.getUuid());
	}
	
	public static class PendingRequest {
		public final UUID requesterId;
		public final String requesterName;
		public final UUID targetId;
		public final String targetName;
		public final long timestamp;
		
		public PendingRequest(UUID requesterId, String requesterName, UUID targetId, String targetName, long timestamp) {
			this.requesterId = requesterId;
			this.requesterName = requesterName;
			this.targetId = targetId;
			this.targetName = targetName;
			this.timestamp = timestamp;
		}
	}
	
	public static class PendingConfirmation {
		public final UUID targetId;
		public final String targetName;
		public final long timestamp;
		
		public PendingConfirmation(UUID targetId, String targetName, long timestamp) {
			this.targetId = targetId;
			this.targetName = targetName;
			this.timestamp = timestamp;
		}
	}
}

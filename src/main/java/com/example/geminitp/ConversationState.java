package com.example.geminitp;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationState {
	private final Map<UUID, PendingAction> pendingActions;
	
	public ConversationState() {
		this.pendingActions = new ConcurrentHashMap<>();
	}
	
	public void setPendingAction(ServerPlayerEntity player, ActionType type, Object data) {
		PendingAction action = new PendingAction(type, data, System.currentTimeMillis());
		pendingActions.put(player.getUuid(), action);
	}
	
	public PendingAction getPendingAction(ServerPlayerEntity player) {
		PendingAction action = pendingActions.get(player.getUuid());
		if (action == null) {
			return null;
		}
		
		// Expire after 2 minutes
		if (System.currentTimeMillis() - action.timestamp > 120000) {
			pendingActions.remove(player.getUuid());
			return null;
		}
		
		return action;
	}
	
	public void clearPendingAction(ServerPlayerEntity player) {
		pendingActions.remove(player.getUuid());
	}
	
	public boolean hasPendingAction(ServerPlayerEntity player) {
		return getPendingAction(player) != null;
	}
	
	public enum ActionType {
		RECORD_LOCATION,
		DEATH_TP,
		PLAYER_TP,
		SPECIAL_TP,
		CREATE_WAYPOINT,
		RENAME_WAYPOINT,
		DELETE_WAYPOINT,
		WAYPOINT_TP
	}
	
	public static class PendingAction {
		public final ActionType type;
		public final Object data;
		public final long timestamp;
		
		public PendingAction(ActionType type, Object data, long timestamp) {
			this.type = type;
			this.data = data;
			this.timestamp = timestamp;
		}
	}
}

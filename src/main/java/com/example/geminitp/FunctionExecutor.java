package com.example.geminitp;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FunctionExecutor {
	private final MinecraftServer server;
	private final DeathLocationTracker deathTracker;
	private final CombatTagManager combatTagManager;
	private final RateLimiter rateLimiter;
	private final WaypointManager waypointManager;
	private final TeleportRequest teleportRequest;
	private final TeleportExecutor teleportExecutor;
	private BegBonus begBonus;
	
	public FunctionExecutor(MinecraftServer server, DeathLocationTracker deathTracker, 
	                        CombatTagManager combatTagManager, RateLimiter rateLimiter,
	                        WaypointManager waypointManager, TeleportRequest teleportRequest) {
		this.server = server;
		this.deathTracker = deathTracker;
		this.combatTagManager = combatTagManager;
		this.rateLimiter = rateLimiter;
		this.waypointManager = waypointManager;
		this.teleportRequest = teleportRequest;
		this.teleportExecutor = new TeleportExecutor(server);
	}

	public void setBegBonus(BegBonus begBonus) {
		this.begBonus = begBonus;
	}
	
	public JsonObject executeFunctionCall(ServerPlayerEntity player, String functionName, JsonObject args) {
		JsonObject result = new JsonObject();
		
		try {
			switch (functionName) {
				case "teleport_to_death":
					return executeDeathTP(player);
				case "teleport_to_player":
					String targetPlayer = args.has("targetPlayer") ? args.get("targetPlayer").getAsString() : null;
					return executePlayerTP(player, targetPlayer);
				case "teleport_to_waypoint":
					String waypointName = args.has("waypointName") ? args.get("waypointName").getAsString() : null;
					return executeWaypointTP(player, waypointName);
				case "create_waypoint":
					String createName = args.has("waypointName") ? args.get("waypointName").getAsString() : null;
					return executeCreateWaypoint(player, createName);
				case "rename_waypoint":
					String oldName = args.has("oldName") ? args.get("oldName").getAsString() : null;
					String newName = args.has("newName") ? args.get("newName").getAsString() : null;
					return executeRenameWaypoint(player, oldName, newName);
				case "delete_waypoint":
					String deleteName = args.has("waypointName") ? args.get("waypointName").getAsString() : null;
					return executeDeleteWaypoint(player, deleteName);
				case "grant_beg_bonus":
					return executeGrantBegBonus(player);
				default:
					result.addProperty("success", false);
					result.addProperty("message", "Unknown function: " + functionName);
					return result;
			}
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Error executing function: " + functionName, e);
			result.addProperty("success", false);
			result.addProperty("message", "Error: " + e.getMessage());
			return result;
		}
	}
	
	private JsonObject executeDeathTP(ServerPlayerEntity player) {
		JsonObject result = new JsonObject();
		
		if (combatTagManager.isInCombat(player)) {
			long remaining = combatTagManager.getRemainingCombatTime(player);
			result.addProperty("success", false);
			result.addProperty("message", "Cannot teleport while in combat (" + (remaining / 1000) + "s remaining)");
			return result;
		}
		
		DeathLocationTracker.DeathLocation deathLoc = deathTracker.getLastDeathLocation(player);
		if (deathLoc == null) {
			result.addProperty("success", false);
			result.addProperty("message", "No death location recorded");
			return result;
		}
		
		if (deathTracker.isDeathLocationUsed(player)) {
			result.addProperty("success", false);
			result.addProperty("message", "Death location already used. Must die again to get a new one.");
			return result;
		}
		
		if (!rateLimiter.canDeathTeleport(player)) {
			result.addProperty("success", false);
			result.addProperty("message", "All death teleports used for this hour (5 max)");
			return result;
		}
		
		rateLimiter.useDeathTeleport(player);
		deathTracker.markDeathLocationUsed(player);
		teleportExecutor.executeTeleport(player, deathLoc.x, deathLoc.y, deathLoc.z, deathLoc.dimension);
		
		sendMessage(player, "§aTeleported to your latest death at " + String.format("%.2f, %.2f, %.2f", deathLoc.x, deathLoc.y, deathLoc.z));
		
		result.addProperty("success", true);
		result.addProperty("message", "Teleported to death location");
		result.addProperty("coordinates", String.format("%.2f, %.2f, %.2f", deathLoc.x, deathLoc.y, deathLoc.z));
		result.addProperty("dimension", deathLoc.dimension);
		
		return result;
	}
	
	private JsonObject executePlayerTP(ServerPlayerEntity player, String targetPlayerName) {
		JsonObject result = new JsonObject();
		
		if (combatTagManager.isInCombat(player)) {
			long remaining = combatTagManager.getRemainingCombatTime(player);
			result.addProperty("success", false);
			result.addProperty("message", "Cannot teleport while in combat (" + (remaining / 1000) + "s remaining)");
			return result;
		}
		
		if (targetPlayerName == null) {
			result.addProperty("success", false);
			result.addProperty("message", "No target player specified");
			return result;
		}
		
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetPlayerName);
		if (target == null) {
			result.addProperty("success", false);
			result.addProperty("message", "Player not found: " + targetPlayerName);
			return result;
		}
		
		if (target.getUuid().equals(player.getUuid())) {
			result.addProperty("success", false);
			result.addProperty("message", "Cannot teleport to yourself");
			return result;
		}
		
		if (combatTagManager.isInCombat(target)) {
			result.addProperty("success", false);
			result.addProperty("message", targetPlayerName + " is in combat and cannot receive teleport requests");
			return result;
		}
		
		if (!rateLimiter.canPlayerTeleport(player)) {
			result.addProperty("success", false);
			result.addProperty("message", "All player teleports used for this hour (20 max)");
			return result;
		}
		
		teleportRequest.createRequest(player, target);
		int queuePosition = teleportRequest.getPendingRequestCount(target);
		
		sendClickableApprovalMessage(target, player.getName().getString(), queuePosition);
		sendMessage(player, "§aSent teleport request to " + targetPlayerName);
		
		result.addProperty("success", true);
		result.addProperty("message", "Teleport request sent to " + targetPlayerName);
		result.addProperty("queuePosition", queuePosition);
		
		return result;
	}
	
	private JsonObject executeWaypointTP(ServerPlayerEntity player, String waypointName) {
		JsonObject result = new JsonObject();
		
		if (combatTagManager.isInCombat(player)) {
			long remaining = combatTagManager.getRemainingCombatTime(player);
			result.addProperty("success", false);
			result.addProperty("message", "Cannot teleport while in combat (" + (remaining / 1000) + "s remaining)");
			return result;
		}
		
		if (waypointName == null || waypointName.isEmpty()) {
			result.addProperty("success", false);
			result.addProperty("message", "No waypoint name specified");
			return result;
		}
		
		WaypointManager.Waypoint waypoint = waypointManager.getWaypoint(player, waypointName);
		if (waypoint == null) {
			result.addProperty("success", false);
			result.addProperty("message", "Waypoint '" + waypointName + "' not found");
			return result;
		}
		
		if (!rateLimiter.canLocationTeleport(player)) {
			result.addProperty("success", false);
			result.addProperty("message", "All special teleports used for this hour (3 max)");
			return result;
		}
		
		rateLimiter.useLocationTeleport(player);
		teleportExecutor.executeTeleport(player, waypoint.x, waypoint.y, waypoint.z, waypoint.dimension);
		
		sendMessage(player, "§aTeleported to waypoint '" + waypointName + "' at " + String.format("%.2f, %.2f, %.2f", waypoint.x, waypoint.y, waypoint.z));
		
		result.addProperty("success", true);
		result.addProperty("message", "Teleported to waypoint '" + waypointName + "'");
		result.addProperty("coordinates", String.format("%.2f, %.2f, %.2f", waypoint.x, waypoint.y, waypoint.z));
		result.addProperty("dimension", waypoint.dimension);
		
		return result;
	}
	
	private JsonObject executeCreateWaypoint(ServerPlayerEntity player, String waypointName) {
		JsonObject result = new JsonObject();
		
		if (waypointName == null || waypointName.isEmpty()) {
			result.addProperty("success", false);
			result.addProperty("message", "No waypoint name specified");
			return result;
		}
		
		if (waypointManager.getRemainingSlots(player) <= 0) {
			result.addProperty("success", false);
			result.addProperty("message", "Maximum of 5 waypoints reached. Delete one first.");
			return result;
		}
		
		if (waypointManager.hasWaypoint(player, waypointName)) {
			result.addProperty("success", false);
			result.addProperty("message", "Waypoint '" + waypointName + "' already exists");
			return result;
		}
		
		if (waypointManager.createWaypoint(player, waypointName)) {
			WaypointManager.Waypoint waypoint = waypointManager.getWaypoint(player, waypointName);
			result.addProperty("success", true);
			result.addProperty("message", "Waypoint '" + waypointName + "' created successfully");
			result.addProperty("coordinates", String.format("%.2f, %.2f, %.2f", waypoint.x, waypoint.y, waypoint.z));
			result.addProperty("dimension", waypoint.dimension);
			
			sendMessage(player, "§aWaypoint '" + waypointName + "' created at " + String.format("%.2f, %.2f, %.2f", waypoint.x, waypoint.y, waypoint.z));
		} else {
			result.addProperty("success", false);
			result.addProperty("message", "Failed to create waypoint");
		}
		
		return result;
	}
	
	private JsonObject executeRenameWaypoint(ServerPlayerEntity player, String oldName, String newName) {
		JsonObject result = new JsonObject();
		
		if (oldName == null || oldName.isEmpty() || newName == null || newName.isEmpty()) {
			result.addProperty("success", false);
			result.addProperty("message", "Both old and new waypoint names must be specified");
			return result;
		}
		
		if (waypointManager.renameWaypoint(player, oldName, newName)) {
			result.addProperty("success", true);
			result.addProperty("message", "Waypoint renamed from '" + oldName + "' to '" + newName + "'");
			
			sendMessage(player, "§aRenamed waypoint '" + oldName + "' to '" + newName + "'");
		} else {
			result.addProperty("success", false);
			result.addProperty("message", "Failed to rename waypoint. It may not exist or the new name is already taken.");
		}
		
		return result;
	}
	
	private JsonObject executeDeleteWaypoint(ServerPlayerEntity player, String waypointName) {
		JsonObject result = new JsonObject();
		
		if (waypointName == null || waypointName.isEmpty()) {
			result.addProperty("success", false);
			result.addProperty("message", "No waypoint name specified");
			return result;
		}
		
		if (waypointManager.deleteWaypoint(player, waypointName)) {
			result.addProperty("success", true);
			result.addProperty("message", "Waypoint '" + waypointName + "' deleted successfully");
			
			sendMessage(player, "§aDeleted waypoint '" + waypointName + "'");
		} else {
			result.addProperty("success", false);
			result.addProperty("message", "Failed to delete waypoint. It may not exist.");
		}
		
		return result;
	}
	
	private JsonObject executeGrantBegBonus(ServerPlayerEntity player) {
		JsonObject result = new JsonObject();

		if (begBonus == null) {
			result.addProperty("success", false);
			result.addProperty("message", "Bonus system not available");
			return result;
		}

		if (begBonus.isActive(player.getUuid())) {
			long remaining = begBonus.getRemainingMs(player.getUuid());
			long hours = remaining / (60 * 60 * 1000);
			long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
			result.addProperty("success", false);
			result.addProperty("message", "Player already has an active beg bonus (" + hours + "h " + minutes + "m remaining)");
			return result;
		}

		if (!begBonus.canEarnAgain(player.getUuid())) {
			result.addProperty("success", false);
			result.addProperty("message", "Player's bonus cooldown hasn't expired yet. They need to wait.");
			return result;
		}

		if (begBonus.grant(player.getUuid())) {
			sendMessage(player, "§d§l§5Oh wow! You found it. §d§l✧");
			sendMessage(player, "§5+1 Waypoint TP §7per hour");
			sendMessage(player, "§5+1 Death TP §7per hour");
			sendMessage(player, "§5+1 Player TP §7per hour");
			sendMessage(player, "§7Expires in 3 hours");
			result.addProperty("success", true);
			result.addProperty("message", "Beg bonus granted! +1 to all TP types per hour for 3 hours.");
			return result;
		}

		result.addProperty("success", false);
		result.addProperty("message", "Failed to grant bonus");
		return result;
	}

	private void sendClickableApprovalMessage(ServerPlayerEntity target, String requesterName, int queuePosition) {
		server.execute(() -> {
			MutableText message = Text.literal("§e" + requesterName + " wants to teleport to you. ");
			
			MutableText acceptButton = Text.literal("§a[ACCEPT]")
				.styled(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/tpapprove accept"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to accept")))
					.withFormatting(Formatting.GREEN).withFormatting(Formatting.BOLD));
			
			message.append(acceptButton);
			message.append(Text.literal(" "));
			
			MutableText denyButton = Text.literal("§c[DENY]")
				.styled(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/tpapprove deny"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to deny")))
					.withFormatting(Formatting.RED).withFormatting(Formatting.BOLD));
			
			message.append(denyButton);
			
			target.sendMessage(message);
			
			if (queuePosition > 1) {
				target.sendMessage(Text.literal("§7You have " + queuePosition + " pending teleport requests"));
			}
		});
	}
	
	private void sendMessage(ServerPlayerEntity player, String message) {
		server.execute(() -> player.sendMessage(Text.literal(message)));
	}
}

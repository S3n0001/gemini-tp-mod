package com.example.geminitp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class FunctionDeclarations {
	
	public static JsonArray getAllFunctionDeclarations() {
		JsonArray functionDeclarations = new JsonArray();
		
		functionDeclarations.add(createDeathTPFunction());
		functionDeclarations.add(createPlayerTPFunction());
		functionDeclarations.add(createWaypointTPFunction());
		functionDeclarations.add(createCreateWaypointFunction());
		functionDeclarations.add(createRenameWaypointFunction());
		functionDeclarations.add(createDeleteWaypointFunction());
		functionDeclarations.add(createGrantBegBonusFunction());
		
		return functionDeclarations;
	}
	
	private static JsonObject createDeathTPFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "teleport_to_death");
		func.addProperty("description", "Teleport the player to their last death location. Can only be used once per death. Costs 1 death TP (5 per hour limit). Cannot be used in combat.");
		
		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		parameters.add("properties", new JsonObject());
		parameters.add("required", new JsonArray());
		
		func.add("parameters", parameters);
		return func;
	}
	
	private static JsonObject createPlayerTPFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "teleport_to_player");
		func.addProperty("description", "Send a teleport request to another player. Requires their approval. Costs 1 player TP (20 per hour limit). Cannot be used if either player is in combat.");
		// whats up prince
		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		
		JsonObject properties = new JsonObject();
		JsonObject targetPlayerProp = new JsonObject();
		targetPlayerProp.addProperty("type", "string");
		targetPlayerProp.addProperty("description", "The name of the player to teleport to");
		properties.add("targetPlayer", targetPlayerProp);
		
		JsonArray required = new JsonArray();
		required.add("targetPlayer");
		
		parameters.add("properties", properties);
		parameters.add("required", required);
		
		func.add("parameters", parameters);
		return func;
	}
	
	private static JsonObject createWaypointTPFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "teleport_to_waypoint");
		func.addProperty("description", "Teleport to a saved waypoint. Costs 1 special TP (3 per hour limit). Cannot be used in combat.");
		
		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		
		JsonObject properties = new JsonObject();
		JsonObject waypointNameProp = new JsonObject();
		waypointNameProp.addProperty("type", "string");
		waypointNameProp.addProperty("description", "The name of the waypoint to teleport to");
		properties.add("waypointName", waypointNameProp);
		
		JsonArray required = new JsonArray();
		required.add("waypointName");
		
		parameters.add("properties", properties);
		parameters.add("required", required);
		
		func.add("parameters", parameters);
		return func;
	}
	
	private static JsonObject createCreateWaypointFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "create_waypoint");
		func.addProperty("description", "Create a new waypoint at the player's current location. FREE (no TP cost). Max 5 waypoints per player.");
		
		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		
		JsonObject properties = new JsonObject();
		JsonObject waypointNameProp = new JsonObject();
		waypointNameProp.addProperty("type", "string");
		waypointNameProp.addProperty("description", "The name for the new waypoint (e.g., 'home', 'base', 'farm')");
		properties.add("waypointName", waypointNameProp);
		
		JsonArray required = new JsonArray();
		required.add("waypointName");
		
		parameters.add("properties", properties);
		parameters.add("required", required);
		
		func.add("parameters", parameters);
		return func;
	}
	
	private static JsonObject createRenameWaypointFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "rename_waypoint");
		func.addProperty("description", "Rename an existing waypoint. FREE (no TP cost).");
		
		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		
		JsonObject properties = new JsonObject();
		
		JsonObject oldNameProp = new JsonObject();
		oldNameProp.addProperty("type", "string");
		oldNameProp.addProperty("description", "The current name of the waypoint");
		properties.add("oldName", oldNameProp);
		
		JsonObject newNameProp = new JsonObject();
		newNameProp.addProperty("type", "string");
		newNameProp.addProperty("description", "The new name for the waypoint");
		properties.add("newName", newNameProp);
		
		JsonArray required = new JsonArray();
		required.add("oldName");
		required.add("newName");
		
		parameters.add("properties", properties);
		parameters.add("required", required);
		
		func.add("parameters", parameters);
		return func;
	}
	
	private static JsonObject createDeleteWaypointFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "delete_waypoint");
		func.addProperty("description", "Delete an existing waypoint. FREE (no TP cost).");
		
		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		
		JsonObject properties = new JsonObject();
		JsonObject waypointNameProp = new JsonObject();
		waypointNameProp.addProperty("type", "string");
		waypointNameProp.addProperty("description", "The name of the waypoint to delete");
		properties.add("waypointName", waypointNameProp);
		
		JsonArray required = new JsonArray();
		required.add("waypointName");
		
		parameters.add("properties", properties);
		parameters.add("required", required);
		
		func.add("parameters", parameters);
		return func;
	}

	private static JsonObject createGrantBegBonusFunction() {
		JsonObject func = new JsonObject();
		func.addProperty("name", "grant_beg_bonus");
		func.addProperty("description", "Grant the player a +1 TP bonus to all teleport types per hour for 3 hours. This is a secret easter egg reward for players who beg, meow, bark, or plead adorably enough. Only call this when the player has truly earned it through persistent cute begging.");

		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");
		parameters.add("properties", new JsonObject());
		parameters.add("required", new JsonArray());

		func.add("parameters", parameters);
		return func;
	}
}

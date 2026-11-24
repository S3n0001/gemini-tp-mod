package com.example.geminitp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationHistory {
	private static final int MAX_MESSAGES_PER_PLAYER = 25; // Keep last 25 exchanges
	private static final long MESSAGE_EXPIRY_MS = 600000; // 10 minutes
	
	private final Map<UUID, LinkedList<Message>> playerHistories;
	private final Gson gson;
	
	public ConversationHistory() {
		this.playerHistories = new ConcurrentHashMap<>();
		this.gson = new Gson();
	}
	
	public void addUserMessage(ServerPlayerEntity player, String message) {
		UUID playerId = player.getUuid();
		LinkedList<Message> history = playerHistories.computeIfAbsent(playerId, k -> new LinkedList<>());
		
		// Clean up old messages
		cleanExpiredMessages(history);
		
		// Add new message
		history.add(new Message("user", message, System.currentTimeMillis()));
		
		// Keep only last N messages
		while (history.size() > MAX_MESSAGES_PER_PLAYER * 2) { // *2 because user + AI messages
			history.removeFirst();
		}
	}
	
	public void addAIMessage(ServerPlayerEntity player, String message) {
		UUID playerId = player.getUuid();
		LinkedList<Message> history = playerHistories.computeIfAbsent(playerId, k -> new LinkedList<>());
		
		// Clean up old messages
		cleanExpiredMessages(history);
		
		// Add new message
		history.add(new Message("assistant", message, System.currentTimeMillis()));
		
		// Keep only last N messages
		while (history.size() > MAX_MESSAGES_PER_PLAYER * 2) {
			history.removeFirst();
		}
	}
	
	/**
	 * Add a system message to conversation history (for internal tracking only).
	 * These are stored as "system" role messages to prevent being output as AI responses.
	 */
	public void addSystemMessage(ServerPlayerEntity player, String message) {
		UUID playerId = player.getUuid();
		LinkedList<Message> history = playerHistories.computeIfAbsent(playerId, k -> new LinkedList<>());
		
		// Clean up old messages
		cleanExpiredMessages(history);
		
		// Add new message with "system" role (internal only)
		history.add(new Message("system", message, System.currentTimeMillis()));
		
		// Keep only last N messages
		while (history.size() > MAX_MESSAGES_PER_PLAYER * 2) {
			history.removeFirst();
		}
	}
	
	public List<Message> getHistory(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		LinkedList<Message> history = playerHistories.get(playerId);
		
		if (history == null) {
			return new ArrayList<>();
		}
		
		// Clean up old messages
		cleanExpiredMessages(history);
		
		return new ArrayList<>(history);
	}
	
	public void clearHistory(ServerPlayerEntity player) {
		playerHistories.remove(player.getUuid());
	}
	
	private void cleanExpiredMessages(LinkedList<Message> history) {
		long currentTime = System.currentTimeMillis();
		history.removeIf(msg -> currentTime - msg.timestamp > MESSAGE_EXPIRY_MS);
	}
	
	public String formatHistoryForPrompt(ServerPlayerEntity player) {
		List<Message> history = getHistory(player);
		
		if (history.isEmpty()) {
			return "";
		}
		
		StringBuilder formatted = new StringBuilder();
		formatted.append("=== CONVERSATION HISTORY (INTERNAL CONTEXT - NEVER SHOW BRACKETS TO PLAYER!) ===\\n");
		formatted.append("⚠️ WARNING: All text in [brackets] below is INTERNAL TRACKING ONLY\\n");
		formatted.append("⚠️ DO NOT echo, mention, or reference ANY bracketed text in your responses to the player\\n");
		formatted.append("⚠️ The player should NEVER see square brackets in your messages\\n");
		formatted.append("Recent conversation:\\n\\n");
		
		for (Message msg : history) {
			if (msg.role.equals("user")) {
				formatted.append("Player: ").append(msg.content).append("\\n");
			} else if (msg.role.equals("assistant") || msg.role.equals("model")) {
				if (msg.functionCall != null) {
					// Show that you called a function - INTERNAL ONLY
					formatted.append("You: [INTERNAL: Called function: ").append(msg.functionCall.name);
					if (msg.functionCall.args != null && msg.functionCall.args.size() > 0) {
						formatted.append(" with args: ").append(msg.functionCall.args);
					}
					formatted.append("] ← DO NOT REPEAT THIS BRACKET TEXT TO PLAYER\\n");
				} else if (msg.content != null) {
					formatted.append("You: ").append(msg.content).append("\\n");
				}
			} else if (msg.role.equals("function")) {
				if (msg.functionResponse != null) {
					// Show the function result - INTERNAL ONLY
					formatted.append("System: [INTERNAL: Function ").append(msg.functionResponse.name).append(" result: ");
					if (msg.functionResponse.response.has("success")) {
						boolean success = msg.functionResponse.response.get("success").getAsBoolean();
						formatted.append(success ? "SUCCESS" : "FAILED");
						if (msg.functionResponse.response.has("message")) {
							formatted.append(" - ").append(msg.functionResponse.response.get("message").getAsString());
						}
					}
					formatted.append("] ← DO NOT REPEAT THIS BRACKET TEXT TO PLAYER\\n");
				}
			} else if (msg.role.equals("system")) {
				// System messages (internal tracking like teleport completion)
				if (msg.content != null) {
					formatted.append(msg.content).append(" ← DO NOT REPEAT THIS BRACKET TEXT TO PLAYER\\n");
				}
			}
		}
		formatted.append("\\n");
		
		return formatted.toString();
	}
	
	public List<JsonObject> getHistoryForApi(ServerPlayerEntity player, String systemPrompt) {
		List<JsonObject> apiHistory = new ArrayList<>();
		
		// Add system prompt as first user message
		JsonObject systemMessage = new JsonObject();
		systemMessage.addProperty("role", "user");
		JsonArray systemParts = new JsonArray();
		JsonObject systemPart = new JsonObject();
		systemPart.addProperty("text", systemPrompt);
		systemParts.add(systemPart);
		systemMessage.add("parts", systemParts);
		apiHistory.add(systemMessage);
		
		// Add conversation history
		List<Message> history = getHistory(player);
		for (Message msg : history) {
			JsonObject apiMsg = new JsonObject();
			apiMsg.addProperty("role", msg.role.equals("assistant") ? "model" : msg.role);
			
			JsonArray parts = new JsonArray();
			
			if (msg.functionCall != null) {
				// This is a tool call request from the model
				JsonObject functionCallPart = new JsonObject();
				JsonObject functionCallObj = new JsonObject();
				functionCallObj.addProperty("name", msg.functionCall.name);
				functionCallObj.add("args", msg.functionCall.args);
				functionCallPart.add("functionCall", functionCallObj);
				parts.add(functionCallPart);
			} else if (msg.functionResponse != null) {
				// This is a tool result
				JsonObject functionResponsePart = new JsonObject();
				JsonObject functionResponseObj = new JsonObject();
				functionResponseObj.addProperty("name", msg.functionResponse.name);
				functionResponseObj.add("response", msg.functionResponse.response);
				functionResponsePart.add("functionResponse", functionResponseObj);
				parts.add(functionResponsePart);
			} else {
				// Regular text message
				JsonObject textPart = new JsonObject();
				textPart.addProperty("text", msg.content);
				parts.add(textPart);
			}
			
			apiMsg.add("parts", parts);
			apiHistory.add(apiMsg);
		}
		
		return apiHistory;
	}
	
	public void addToolCallRequest(ServerPlayerEntity player, String functionName, JsonObject functionArgs) {
		UUID playerId = player.getUuid();
		LinkedList<Message> history = playerHistories.computeIfAbsent(playerId, k -> new LinkedList<>());
		cleanExpiredMessages(history);
		
		// Store the function call in the message
		Message msg = new Message("model", null, System.currentTimeMillis());
		msg.functionCall = new FunctionCallData(functionName, functionArgs);
		history.add(msg);
		
		while (history.size() > MAX_MESSAGES_PER_PLAYER * 2) {
			history.removeFirst();
		}
	}
	
	public void addToolResult(ServerPlayerEntity player, String functionName, JsonObject result) {
		UUID playerId = player.getUuid();
		LinkedList<Message> history = playerHistories.computeIfAbsent(playerId, k -> new LinkedList<>());
		cleanExpiredMessages(history);
		
		Message msg = new Message("function", null, System.currentTimeMillis());
		msg.functionResponse = new FunctionResponseData(functionName, result);
		history.add(msg);
		
		while (history.size() > MAX_MESSAGES_PER_PLAYER * 2) {
			history.removeFirst();
		}
	}
	
	public static class Message {
		public final String role; // "user", "assistant", "model", "function", or "system"
		public final String content;
		public final long timestamp;
		public FunctionCallData functionCall;
		public FunctionResponseData functionResponse;
		
		public Message(String role, String content, long timestamp) {
			this.role = role;
			this.content = content;
			this.timestamp = timestamp;
		}
	}
	
	public static class FunctionCallData {
		public final String name;
		public final JsonObject args;
		
		public FunctionCallData(String name, JsonObject args) {
			this.name = name;
			this.args = args;
		}
	}
	
	public static class FunctionResponseData {
		public final String name;
		public final JsonObject response;
		
		public FunctionResponseData(String name, JsonObject response) {
			this.name = name;
			this.response = response;
		}
	}
}

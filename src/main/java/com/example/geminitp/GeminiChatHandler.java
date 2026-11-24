package com.example.geminitp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main coordinator for AI chat interactions.
 * Uses modular components for clean separation of concerns.
 */
public class GeminiChatHandler {
	private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String API_KEY = resolveApiKey();

	private static String resolveApiKey() {
		String key = System.getenv("GEMINI_API_KEY");
		if (key == null || key.isBlank()) {
			throw new IllegalStateException(
				"GEMINI_API_KEY environment variable is not set. " +
				"Set it before starting the server (see .env.example).");
		}
		return key;
	}

	private final MinecraftServer server;
	private final SystemPromptBuilder promptBuilder;
	private final FunctionExecutor functionExecutor;
	private final ConversationHistory conversationHistory;
	private final TeleportApprovalHandler approvalHandler;
	private final RateLimiter rateLimiter;
	private final LocationSaver locationSaver;
	private final WaypointManager waypointManager;
	private final TeleportExecutor teleportExecutor;
	private final TeleportRequest teleportRequest;
	private final CombatTagManager combatTagManager;
	private final OkHttpClient httpClient;
	private final ExecutorService executor;
	private final Gson gson;
	private final ClickableTextParser clickableTextParser;
	private final ThinkingSpinner thinkingSpinner;
	private int messageCounter = 0;
	
	public GeminiChatHandler(MinecraftServer server, DeathLocationTracker deathTracker, CombatTagManager combatTagManager, ConversationHistory conversationHistory) {
		this.server = server;
		this.rateLimiter = new RateLimiter();
		this.locationSaver = new LocationSaver();
		this.waypointManager = new WaypointManager();
		this.teleportRequest = new TeleportRequest();
		this.conversationHistory = conversationHistory;
		this.combatTagManager = combatTagManager;
		this.teleportExecutor = new TeleportExecutor(server);
		this.clickableTextParser = new ClickableTextParser(this);
		this.thinkingSpinner = new ThinkingSpinner(server);
		
		// Initialize modular components
		this.promptBuilder = new SystemPromptBuilder(server, deathTracker, combatTagManager, 
			rateLimiter, waypointManager, conversationHistory, GeminiTpMod.getTsundereManager(),
			GeminiTpMod.getPersonalityConfig());
		this.functionExecutor = new FunctionExecutor(server, deathTracker, combatTagManager, 
			rateLimiter, waypointManager, this.teleportRequest);
		this.approvalHandler = new TeleportApprovalHandler(server, combatTagManager, rateLimiter, 
			this.teleportRequest, this.teleportExecutor);
		
		this.httpClient = new OkHttpClient.Builder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.build();
		this.executor = Executors.newFixedThreadPool(4);
		this.gson = new Gson();
	}

	
	public void handleChatMessage(ServerPlayerEntity player, String message) {
		if (!message.toLowerCase().startsWith("@ai ")) {
			return;
		}
		
		String query = message.substring(4).trim();
		if (query.isEmpty()) {
			return;
		}
		
		CompletableFuture.runAsync(() -> processAIRequest(player, query), executor);
	}
	
	private void processAIRequest(ServerPlayerEntity player, String query) {
		conversationHistory.addUserMessage(player, query);
		thinkingSpinner.start(player);
		
		try {
			String systemPrompt = promptBuilder.buildSystemPrompt(player, query);
			processAIConversation(player, systemPrompt, query);
		} catch (Exception e) {
			GeminiTpMod.LOGGER.error("Error processing AI request", e);
			sendMessage(player, "§cError communicating with AI");
		} finally {
			thinkingSpinner.stop(player);
		}
	}
	
	private void processAIConversation(ServerPlayerEntity player, String systemPrompt, String userQuery) throws IOException {
		JsonObject requestBody = buildFunctionCallingRequest(systemPrompt, userQuery);
		
		Request request = new Request.Builder()
			.url(GEMINI_API_URL + "?key=" + API_KEY)
			.post(RequestBody.create(requestBody.toString(), JSON))
			.build();
		
		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				String errorBody = response.body() != null ? response.body().string() : "null";
				GeminiTpMod.LOGGER.error("API call failed: {} - Body: {}", response.code(), errorBody);
				sendMessage(player, "§cError communicating with AI");
				return;
			}
			
			String responseBody = response.body().string();
			JsonObject apiResponse = gson.fromJson(responseBody, JsonObject.class);
			
			handleAIResponse(player, apiResponse, systemPrompt);
		}
	}
	
	private JsonObject buildFunctionCallingRequest(String systemPrompt, String userQuery) {
		JsonObject requestBody = new JsonObject();

		// Add contents
		JsonArray contents = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");

		JsonArray parts = new JsonArray();
		JsonObject textPart = new JsonObject();
		textPart.addProperty("text", systemPrompt + "\n\nUser Query: " + userQuery);
		parts.add(textPart);

		userMessage.add("parts", parts);
		contents.add(userMessage);

		requestBody.add("contents", contents);

		// Add function declarations (tools) from modular component
		JsonArray tools = new JsonArray();
		JsonObject toolsObject = new JsonObject();
		toolsObject.add("function_declarations", FunctionDeclarations.getAllFunctionDeclarations());
		tools.add(toolsObject);

		requestBody.add("tools", tools);

		// Configure the model generation
		JsonObject generationConfig = new JsonObject();
		JsonObject thinkingConfig = new JsonObject();

		// Use thinkingLevel instead of thinkingBudget for newer Gemini models
		thinkingConfig.addProperty("thinkingLevel", "MINIMAL");

		generationConfig.add("thinkingConfig", thinkingConfig);
		requestBody.add("generationConfig", generationConfig);

		return requestBody;
	}
	
	private void handleAIResponse(ServerPlayerEntity player, JsonObject apiResponse, String systemPrompt) throws IOException {
		if (apiResponse == null || !apiResponse.has("candidates")) {
			return;
		}
		
		JsonArray candidates = apiResponse.getAsJsonArray("candidates");
		if (candidates.size() == 0) {
			return;
		}
		
		JsonObject candidate = candidates.get(0).getAsJsonObject();
		if (!candidate.has("content")) {
			return;
		}
		
		JsonObject content = candidate.getAsJsonObject("content");
		if (!content.has("parts")) {
			return;
		}
		
		JsonArray parts = content.getAsJsonArray("parts");
		StringBuilder textBeforeFunction = new StringBuilder();
		boolean hasFunctionCall = false;
		String functionName = null;
		JsonObject functionArgs = null;
		
		// First pass: collect text before function call and detect function call
		for (int i = 0; i < parts.size(); i++) {
			JsonObject part = parts.get(i).getAsJsonObject();
			
			if (part.has("text") && !hasFunctionCall) {
				textBeforeFunction.append(part.get("text").getAsString());
			}
			
			if (part.has("functionCall")) {
				hasFunctionCall = true;
				JsonObject functionCall = part.getAsJsonObject("functionCall");
				functionName = functionCall.get("name").getAsString();
				functionArgs = functionCall.has("args") ? functionCall.getAsJsonObject("args") : new JsonObject();
			}
		}
		
		// Send text before function call if any
		String beforeText = textBeforeFunction.toString().trim();
		if (!beforeText.isEmpty()) {
			conversationHistory.addAIMessage(player, beforeText);
			sendAIMessage(player, beforeText);
		}
		
		// Execute function if present
		if (hasFunctionCall) {
			// Track the function call request
			conversationHistory.addToolCallRequest(player, functionName, functionArgs);
			
			JsonObject result = functionExecutor.executeFunctionCall(player, functionName, functionArgs);
			
			// Track the function result
			conversationHistory.addToolResult(player, functionName, result);
			
			// Get AI's response after function execution
			String afterText = callGeminiWithFunctionResult(player, systemPrompt, functionName, functionArgs, result);
			
			if (afterText != null) {
				afterText = afterText.trim();
				if (!afterText.isEmpty()) {
					conversationHistory.addAIMessage(player, afterText);
					// Send without [AI] prefix since it's continuation of same response
					sendAIMessage(player, afterText);
				}
			}
			return;
		}
		
		// No function call, just text response
		if (textBeforeFunction.length() > 0) {
			// Already sent above
			return;
		}
	}
	
	private String callGeminiWithFunctionResult(ServerPlayerEntity player, String systemPrompt, 
	                                            String functionName, JsonObject args, JsonObject result) throws IOException {
		JsonObject requestBody = new JsonObject();
		JsonArray contents = new JsonArray();
		
		// Add original user message
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		JsonArray userParts = new JsonArray();
		JsonObject userTextPart = new JsonObject();
		userTextPart.addProperty("text", systemPrompt);
		userParts.add(userTextPart);
		userMessage.add("parts", userParts);
		contents.add(userMessage);
		
		// Add model's function call
		JsonObject modelMessage = new JsonObject();
		modelMessage.addProperty("role", "model");
		JsonArray modelParts = new JsonArray();
		JsonObject functionCallPart = new JsonObject();
		JsonObject functionCall = new JsonObject();
		functionCall.addProperty("name", functionName);
		functionCall.add("args", args);
		functionCallPart.add("functionCall", functionCall);
		modelParts.add(functionCallPart);
		modelMessage.add("parts", modelParts);
		contents.add(modelMessage);
		
		// Add function response
		JsonObject functionResponseMessage = new JsonObject();
		functionResponseMessage.addProperty("role", "user");
		JsonArray functionResponseParts = new JsonArray();
		JsonObject functionResponsePart = new JsonObject();
		JsonObject functionResponse = new JsonObject();
		functionResponse.addProperty("name", functionName);
		functionResponse.add("response", result);
		functionResponsePart.add("functionResponse", functionResponse);
		functionResponseParts.add(functionResponsePart);
		functionResponseMessage.add("parts", functionResponseParts);
		contents.add(functionResponseMessage);
		
		requestBody.add("contents", contents);
		
		Request request = new Request.Builder()
			.url(GEMINI_API_URL + "?key=" + API_KEY)
			.post(RequestBody.create(requestBody.toString(), JSON))
			.build();
		
		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				return null;
			}
			
			String responseBody = response.body().string();
			JsonObject apiResponse = gson.fromJson(responseBody, JsonObject.class);
			
			if (apiResponse != null && apiResponse.has("candidates")) {
				JsonArray candidates = apiResponse.getAsJsonArray("candidates");
				if (candidates.size() > 0) {
					JsonObject candidate = candidates.get(0).getAsJsonObject();
					if (candidate.has("content")) {
						JsonObject content = candidate.getAsJsonObject("content");
						if (content.has("parts")) {
							JsonArray parts = content.getAsJsonArray("parts");
							if (parts.size() > 0) {
								JsonObject part = parts.get(0).getAsJsonObject();
								if (part.has("text")) {
									return part.get("text").getAsString();
								}
							}
						}
					}
				}
			}
		}
		
		return null;
	}
	
	public void handleTeleportApproval(ServerPlayerEntity targetPlayer, boolean approved) {
		approvalHandler.handleTeleportApproval(targetPlayer, approved);
	}
	
	public void confirmTeleport(ServerPlayerEntity player, boolean confirmed) {
		TeleportRequest.PendingConfirmation confirmation = teleportRequest.getPendingConfirmation(player);
		
		if (confirmation == null) {
			sendMessage(player, "§cNo pending teleport confirmation");
			return;
		}
		
		teleportRequest.removePendingConfirmation(player);
		
		if (!confirmed) {
			sendMessage(player, "§eTeleport cancelled");
			return;
		}
		
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(confirmation.targetId);
		if (target == null) {
			sendMessage(player, "§c" + confirmation.targetName + " is no longer online");
			return;
		}
		
		if (combatTagManager.isInCombat(player)) {
			long remaining = combatTagManager.getRemainingCombatTime(player);
			sendMessage(player, "§cYou are in combat and cannot teleport! (" + (remaining / 1000) + "s remaining)");
			return;
		}
		
		if (combatTagManager.isInCombat(target)) {
			long remaining = combatTagManager.getRemainingCombatTime(target);
			sendMessage(player, "§c" + confirmation.targetName + " is in combat! (" + (remaining / 1000) + "s remaining)");
			return;
		}
		
		if (!rateLimiter.canPlayerTeleport(player)) {
			sendMessage(player, "§cAll player teleports used for this hour (20 max)");
			return;
		}
		
		rateLimiter.usePlayerTeleport(player);
		
		double x = target.getX();
		double y = target.getY();
		double z = target.getZ();
		String dimension = target.getCommandSource().getWorld().getRegistryKey().getValue().toString();
		
		teleportExecutor.executeTeleport(player, x, y, z, dimension);
		sendMessage(player, "§aTeleported to " + confirmation.targetName);
		sendMessage(target, "§e" + player.getName().getString() + " teleported to you");
		
		// Add to conversation history so AI knows the teleport completed
		// Store as a "function" role message (system context) NOT as AI message to prevent accidental output
		conversationHistory.addSystemMessage(player, "[INTERNAL: System: Player teleport to " + confirmation.targetName + " completed successfully]");
	}
	
	private void sendMessage(ServerPlayerEntity player, String message) {
		// 1. Normalize quotes first (convert smart quotes to straight quotes for button parsing)
		// This ensures ["text"=ACCEPT] works even if AI uses smart quotes
		message = message.replace("\u201C", "\"").replace("\u201D", "\"")
		                 .replace("\u2018", "'").replace("\u2019", "'");
		
		// 2. Filter out any bracketed internal messages
		String filteredMessage = filterInternalText(message);
		if (!filteredMessage.isEmpty()) {
			// Generate unique message ID
			String messageId = player.getUuidAsString() + "_" + (messageCounter++);
			
			// Parse message for clickable buttons AFTER filtering
			MutableText parsedMessage = clickableTextParser.parseMessage(player, filteredMessage, messageId);
			
			server.execute(() -> player.sendMessage(parsedMessage));
		}
	}

	private void sendAIMessage(ServerPlayerEntity player, String message) {
		// 1. Normalize quotes first
		message = message.replace("\u201C", "\"").replace("\u201D", "\"")
		                 .replace("\u2018", "'").replace("\u2019", "'");
		
		// 2. Filter out any bracketed internal messages
		String filteredMessage = filterInternalText(message);
		if (filteredMessage.isEmpty()) {
			return;
		}
			
		// Generate unique message ID
		String messageId = player.getUuidAsString() + "_" + (messageCounter++);
		
		// Parse message for clickable buttons
		MutableText content = clickableTextParser.parseMessage(player, filteredMessage, messageId);
		
		// Create interactive prefix
		MutableText prefix = Text.literal("[AI]:").styled(style -> style
			.withColor(Formatting.AQUA)
			.withHoverEvent(new HoverEvent.ShowText(
				Text.literal("Artificial Intelligence\nThis is currently in its experimental phase, there may still be bugs, artificial intelligence can make mistakes proceed with caution.")
					.formatted(Formatting.GRAY)
			))
		);
		
		// Combine prefix and content using a clean root to prevent style inheritance (hover spillover)
		MutableText fullMessage = Text.literal("")
			.append(prefix)
			.append(Text.literal(" "))
			.append(content);
		
		server.execute(() -> player.sendMessage(fullMessage));
	}
	
	/**
	 * Filters out bracketed internal messages that the AI shouldn't show to players.
	 * Removes patterns like:
	 * - [Internal: ...]
	 * - [INTERNAL: ...]
	 * - [System: ...]
	 * BUT keeps [AI] and [AI]: prefixes
	 */
	private String filterInternalText(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		
		// 1. Remove the specific reminder text that appears in history
		// Handle both arrow types just in case
		text = text.replace("← DO NOT REPEAT THIS BRACKET TEXT TO PLAYER", "")
		           .replace("<- DO NOT REPEAT THIS BRACKET TEXT TO PLAYER", "");
		
		// 2. Remove [INTERNAL: ...] or [System: ...] patterns
		// Also remove "System:" or "You:" prefixes if they appear before the bracket
		// This handles cases where AI echoes "System: [INTERNAL: ...]"
		text = text.replaceAll("(?i)(System:|You:)?\\s*\\[(internal|system)\\s*:[^\\]]*\\]", "");
		
		// 3. Normalize whitespace
		// Convert Windows line endings
		text = text.replace("\r\n", "\n").replace("\r", "\n");
		
		// Collapse multiple horizontal spaces/tabs into one single space
		text = text.replaceAll("[ \\t]+", " ");
		
		// Remove spaces around newlines (e.g. " \n " -> "\n")
		text = text.replaceAll(" *\\n *", "\n");
		
		// Collapse 3+ newlines into 2 (preserve paragraph breaks, remove excessive vertical space)
		text = text.replaceAll("\\n{3,}", "\n\n");
		
		return text.trim();
	}
	
	public ConversationHistory getConversationHistory() {
		return conversationHistory;
	}
	
	public ClickableTextParser getClickableTextParser() {
		return clickableTextParser;
	}
	
	public ThinkingSpinner getThinkingSpinner() {
		return thinkingSpinner;
	}

	/** Wire the beg bonus into the rate limiter and function executor. */
	public void setBegBonus(BegBonus begBonus) {
		rateLimiter.setBegBonus(begBonus);
		functionExecutor.setBegBonus(begBonus);
	}
	
	/**
	 * Handle when a player clicks a button in an AI message.
	 * Supports classic ACCEPT/DENY and custom action strings from rich buttons.
	 */
	public void handleAIButtonResponse(ServerPlayerEntity player, String action) {
		// Add the player's response to conversation history
		String userResponse = "Player clicked: " + action;
		conversationHistory.addUserMessage(player, userResponse);
		
		// Build a natural feedback message for the AI based on the action
		String feedbackMessage;
		if (action.equals("ACCEPT")) {
			feedbackMessage = "Player accepted the previous suggestion";
		} else if (action.equals("DENY")) {
			feedbackMessage = "Player denied the previous suggestion";
		} else {
			// Custom action — pass the action name so the AI knows what was chosen
			feedbackMessage = "Player selected option: " + action;
		}
		
		// Process AI request with the feedback
		CompletableFuture.runAsync(() -> {
			thinkingSpinner.start(player);
			try {
				String systemPrompt = promptBuilder.buildSystemPrompt(player, feedbackMessage);
				processAIConversation(player, systemPrompt, feedbackMessage);
			} catch (Exception e) {
				GeminiTpMod.LOGGER.error("Error processing AI button response", e);
			} finally {
				thinkingSpinner.stop(player);
			}
		}, executor);
	}
	
	public void shutdown() {
		thinkingSpinner.shutdown();
		rateLimiter.shutdown();
		locationSaver.shutdown();
		waypointManager.shutdown();
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
		}
	}
}

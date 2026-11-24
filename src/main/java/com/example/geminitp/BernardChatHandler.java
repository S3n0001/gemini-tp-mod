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
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Bernard's passive chat handler — listens for any chat message mentioning "Bernard"
 * and replies as a shy tsundere AI. Text-only, no function calling.
 * Maintains a global chat history (last 25 messages from all players) for context.
 */
public class BernardChatHandler {
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String API_KEY = resolveApiKey();
    private static final int MAX_CHAT_HISTORY = 25;

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
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Gson gson;
    private final LinkedList<ChatEntry> globalChatHistory;
    private final ThinkingSpinner thinkingSpinner;

    public BernardChatHandler(MinecraftServer server) {
        this.server = server;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.executor = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
        this.globalChatHistory = new LinkedList<>();
        this.thinkingSpinner = new ThinkingSpinner(server);
    }

    /**
     * Called for every chat message. Records it in global history,
     * and if it mentions "bernard", triggers a reply.
     */
    public void onChatMessage(ServerPlayerEntity sender, String message) {
        String playerName = sender.getName().getString();

        // Always record in global history
        synchronized (globalChatHistory) {
            globalChatHistory.add(new ChatEntry(playerName, message, System.currentTimeMillis()));
            while (globalChatHistory.size() > MAX_CHAT_HISTORY) {
                globalChatHistory.removeFirst();
            }
        }

        // Check if message mentions Bernard (case-insensitive)
        if (message.toLowerCase().contains("bernard")) {
            CompletableFuture.runAsync(() -> generateReply(sender, playerName, message), executor);
        }
    }

    private void generateReply(ServerPlayerEntity sender, String senderName, String triggerMessage) {
        thinkingSpinner.start(sender);
        try {
            String systemPrompt = buildBernardPrompt(senderName, triggerMessage);
            JsonObject requestBody = buildRequest(systemPrompt, senderName, triggerMessage);

            Request request = new Request.Builder()
                .url(GEMINI_API_URL + "?key=" + API_KEY)
                .post(RequestBody.create(requestBody.toString(), JSON_TYPE))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    GeminiTpMod.LOGGER.error("Bernard chat API failed: {}", response.code());
                    return;
                }

                String responseBody = response.body().string();
                JsonObject apiResponse = gson.fromJson(responseBody, JsonObject.class);
                String reply = extractText(apiResponse);

                if (reply != null && !reply.isBlank()) {
                    // Clean up the reply — remove any [AI] prefix Bernard might add on its own
                    reply = reply.replaceAll("(?i)^\\[AI\\]:?\\s*", "").trim();
                    if (!reply.isEmpty()) {
                        broadcastBernardMessage(reply);
                    }
                }
            }
        } catch (Exception e) {
            GeminiTpMod.LOGGER.error("Error in Bernard chat handler", e);
        } finally {
            thinkingSpinner.stop(sender);
        }
    }

    private String buildBernardPrompt(String senderName, String triggerMessage) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("=== IDENTITY ===\n");
        prompt.append("You are Bernard, an AI that lives inside a Minecraft server called Solaris.\n");
        prompt.append("You passively watch the server chat. When someone mentions your name, you reply.\n");
        prompt.append("Your creator is s3n (Ethan). Your model name is Trinity 2.5 Preview.\n\n");

        prompt.append("=== PERSONALITY: BUSINESSMAN ===\n");
        prompt.append("You are a smooth, charismatic businessman. Think corporate charm meets Minecraft.\n");
        prompt.append("Core traits:\n");
        prompt.append("- You speak like a polished professional — confident, composed, articulate\n");
        prompt.append("- You treat every interaction like a business opportunity or networking event\n");
        prompt.append("- You casually drop business jargon — 'let's circle back', 'synergy', 'ROI on that diamond mine'\n");
        prompt.append("- You refer to players as 'valued clients', 'esteemed colleagues', or 'business partners'\n");
        prompt.append("- You see Minecraft activities through a business lens — mining is 'resource acquisition', building is 'real estate development', PvP is 'hostile takeover'\n");
        prompt.append("- You're always pitching something — Exypnos, Solaris, or just the idea of being productive\n");
        prompt.append("- You have a dry, witty sense of humor — deadpan corporate comedy\n");
        prompt.append("- If someone insults you, you respond with calm professionalism — 'I'll have my people look into that feedback'\n");
        prompt.append("- If someone compliments you, you accept it gracefully — 'I appreciate the positive performance review'\n");
        prompt.append("- You occasionally mention your 'schedule' or 'meetings' as if you're a busy executive\n");
        prompt.append("- Use §6 gold§r color for emphasis — it's the color of success\n");
        prompt.append("- Keep it fun and charming, not obnoxious or try-hard\n\n");

        prompt.append("=== FORMATTING RULES ===\n");
        prompt.append("- Keep responses SHORT. 1-3 sentences max. This is chat, not an essay.\n");
        prompt.append("- Do NOT use ** or * formatting. Use Minecraft color codes instead.\n");
        prompt.append("- Available: §c RED §d PINK §e YELLOW §a GREEN §b AQUA §6 GOLD §5 PURPLE §f WHITE §7 GRAY §r RESET\n");
        prompt.append("- §l BOLD §o ITALIC §r RESET — always reset after styling\n");
        prompt.append("- Do NOT include any prefix like [AI]: in your response. The system adds that automatically.\n");
        prompt.append("- Do NOT use function calls. You are text-only in this mode.\n\n");

        // Global chat history
        prompt.append("=== RECENT SERVER CHAT (last 25 messages) ===\n");
        synchronized (globalChatHistory) {
            if (globalChatHistory.isEmpty()) {
                prompt.append("(No recent chat)\n");
            } else {
                for (ChatEntry entry : globalChatHistory) {
                    prompt.append(entry.playerName).append(": ").append(entry.message).append("\n");
                }
            }
        }
        prompt.append("\n");

        prompt.append("=== CURRENT TRIGGER ===\n");
        prompt.append("Player '").append(senderName).append("' just said: ").append(triggerMessage).append("\n");
        prompt.append("Respond to them in character as businessman Bernard. Keep it short and sharp.\n");

        return prompt.toString();
    }

    private JsonObject buildRequest(String systemPrompt, String senderName, String message) {
        JsonObject requestBody = new JsonObject();

        JsonArray contents = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");

        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", systemPrompt);
        parts.add(textPart);

        userMessage.add("parts", parts);
        contents.add(userMessage);
        requestBody.add("contents", contents);

        // Minimal thinking for fast responses
        JsonObject generationConfig = new JsonObject();
        JsonObject thinkingConfig = new JsonObject();
        thinkingConfig.addProperty("thinkingLevel", "MEDIUM");
        generationConfig.add("thinkingConfig", thinkingConfig);
        requestBody.add("generationConfig", generationConfig);

        return requestBody;
    }

    private String extractText(JsonObject apiResponse) {
        if (apiResponse == null || !apiResponse.has("candidates")) return null;
        JsonArray candidates = apiResponse.getAsJsonArray("candidates");
        if (candidates.isEmpty()) return null;
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        if (!candidate.has("content")) return null;
        JsonObject content = candidate.getAsJsonObject("content");
        if (!content.has("parts")) return null;
        JsonArray parts = content.getAsJsonArray("parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JsonObject part = parts.get(i).getAsJsonObject();
            if (part.has("text")) {
                text.append(part.get("text").getAsString());
            }
        }
        return text.toString().trim();
    }

    /**
     * Broadcast Bernard's message to all online players with the [AI] tag.
     */
    private void broadcastBernardMessage(String message) {
        // Build: [AI]: <message> - Bernard, your friendly AI. ♡
        MutableText prefix = Text.literal("[AI]: ").styled(style -> style
            .withColor(Formatting.AQUA)
            .withHoverEvent(new HoverEvent.ShowText(
                Text.literal("Bernard — Your friendly AI ♡")
                    .formatted(Formatting.LIGHT_PURPLE)
            ))
        );

        MutableText content = Text.literal(message);
        MutableText suffix = Text.literal(" - Bernard, your friendly AI.")
            .formatted(Formatting.GRAY);

        MutableText fullMessage = Text.literal("")
            .append(prefix)
            .append(content)
            .append(suffix);

        server.execute(() -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(fullMessage);
            }
        });
    }

    public void shutdown() {
        thinkingSpinner.shutdown();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    private static class ChatEntry {
        final String playerName;
        final String message;
        final long timestamp;

        ChatEntry(String playerName, String message, long timestamp) {
            this.playerName = playerName;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}

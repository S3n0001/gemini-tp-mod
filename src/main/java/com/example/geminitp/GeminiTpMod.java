package com.example.geminitp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiTpMod implements ModInitializer {
	public static final String MOD_ID = "geminitp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private static MinecraftServer server;
	private static DeathLocationTracker deathTracker;
	private static GeminiChatHandler chatHandler;
	private static BernardChatHandler bernardChatHandler;
	private static CombatTagManager combatTagManager;
	private static CombatHudManager combatHudManager;
	private static CombatLogHandler combatLogHandler;
	private static CombatDisplayConfig combatDisplayConfig;
	private static BegBonus begBonus;
	private static TsundereManager tsundereManager;
	private static ConversationHistory conversationHistory;
	private static PersonalityConfig personalityConfig;
	
	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Gemini TP Mod");
		
		// Initialize systems
		deathTracker = new DeathLocationTracker();
		combatTagManager = new CombatTagManager();
		combatDisplayConfig = new CombatDisplayConfig(new java.io.File("config/geminitp/combat_display.json"));
		begBonus = new BegBonus(new java.io.File("config/geminitp/beg_bonus.json"));
		tsundereManager = new TsundereManager(new java.io.File("config/geminitp/tsundere_users.json"));
		conversationHistory = new ConversationHistory();
		personalityConfig = new PersonalityConfig(new java.io.File("config/geminitp/personality.json"));
		
		// Register all commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TeleportCommand.register(dispatcher);
			TsundereCommand.register(dispatcher, registryAccess, environment, tsundereManager, conversationHistory);
			BernardCommand.register(dispatcher, registryAccess, environment, conversationHistory);
			AIResponseCommand.register(dispatcher);
			PersonalityCommand.register(dispatcher, registryAccess, environment, personalityConfig, conversationHistory);
			
			// /thinking — toggle the thinking indicator per player (no permissions needed)
			dispatcher.register(net.minecraft.server.command.CommandManager.literal("thinking")
				.executes(context -> {
					if (!(context.getSource().getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player)) return 0;
					if (chatHandler == null) return 0;
					boolean enabled = chatHandler.getThinkingSpinner().toggle(player.getUuid());
					player.sendMessage(net.minecraft.text.Text.literal(
						enabled ? "§aThinking indicator enabled." : "§7Thinking indicator disabled."));
					return 1;
				}));

			// /combattimer [mode] — view or change combat HUD display mode
			dispatcher.register(net.minecraft.server.command.CommandManager.literal("combattimer")
				.executes(context -> {
					// No args — show current mode and list all options
					if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0;
					String name = player.getName().getString();
					CombatDisplayConfig.DisplayMode current = combatDisplayConfig.getDisplayMode(name);
					player.sendMessage(net.minecraft.text.Text.literal(
						"§6§lCombat Timer Display§r\n" +
						"§7Current: " + combatDisplayConfig.getDisplayModeDescription(current) + "\n" +
						"§7Modes: §f/combattimer always§7, §f/combattimer clean§7, §f/combattimer hidden§7, §f/combattimer pulse\n" +
						"§7Sound: " + (combatDisplayConfig.isSoundEnabled(name) ? "§aON" : "§cOFF") + " §7— §f/combatsound§7 to toggle"));
					return 1;
				})
				.then(net.minecraft.server.command.CommandManager.literal("always")
					.executes(context -> {
						if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0;
						combatDisplayConfig.setDisplayMode(player.getName().getString(), CombatDisplayConfig.DisplayMode.ALWAYS);
						player.sendMessage(net.minecraft.text.Text.literal("§aCombat timer set to §fAlways§a — full display with warning text."));
						return 1;
					}))
				.then(net.minecraft.server.command.CommandManager.literal("clean")
					.executes(context -> {
						if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0;
						combatDisplayConfig.setDisplayMode(player.getName().getString(), CombatDisplayConfig.DisplayMode.ALWAYS_CLEAN);
						player.sendMessage(net.minecraft.text.Text.literal("§aCombat timer set to §fAlways (Clean)§a — no warning text."));
						return 1;
					}))
				.then(net.minecraft.server.command.CommandManager.literal("hidden")
					.executes(context -> {
						if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0;
						combatDisplayConfig.setDisplayMode(player.getName().getString(), CombatDisplayConfig.DisplayMode.HIDDEN);
						player.sendMessage(net.minecraft.text.Text.literal("§7Combat timer §fhidden§7. You won't see the HUD."));
						return 1;
					}))
				.then(net.minecraft.server.command.CommandManager.literal("pulse")
					.executes(context -> {
						if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0;
						combatDisplayConfig.setDisplayMode(player.getName().getString(), CombatDisplayConfig.DisplayMode.PULSE);
						player.sendMessage(net.minecraft.text.Text.literal("§aCombat timer set to §fPulse§a — shows 3s, hides 5s, repeats."));
						return 1;
					})));

			// /combatsound — toggle the wither sound on combat entry
			dispatcher.register(net.minecraft.server.command.CommandManager.literal("combatsound")
				.executes(context -> {
					if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0;
					combatDisplayConfig.toggleSound(player.getName().getString());
					boolean enabled = combatDisplayConfig.isSoundEnabled(player.getName().getString());
					player.sendMessage(net.minecraft.text.Text.literal(
						enabled ? "§aCombat sound enabled." : "§7Combat sound disabled."));
					return 1;
				}));
			
			LOGGER.info("Registered all commands");
		});
		
		// Register server started event
		ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
			server = minecraftServer;
			chatHandler = new GeminiChatHandler(server, deathTracker, combatTagManager, conversationHistory);
			chatHandler.setBegBonus(begBonus);
			bernardChatHandler = new BernardChatHandler(server);
			combatHudManager = new CombatHudManager(server, combatTagManager, combatDisplayConfig);
			combatLogHandler = new CombatLogHandler(server, combatTagManager);
			combatTagManager.setServer(server);
			// Link the thinking spinner so combat HUD can yield to it
			combatHudManager.setThinkingSpinner(chatHandler.getThinkingSpinner());
			LOGGER.info("Server started, chat handler initialized");
		});
		
		// Register server stopping event
		ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
			if (chatHandler != null) {
				chatHandler.shutdown();
			}
			if (bernardChatHandler != null) {
				bernardChatHandler.shutdown();
			}
			if (combatHudManager != null) {
				combatHudManager.shutdown();
			}
			LOGGER.info("Server stopping, cleaning up");
		});
		
		// Register player damage event for combat tagging
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player && amount > 0) {
				combatTagManager.tagPlayer(player, source);
				// Notify HUD manager to start display + play sound on entry
				if (combatHudManager != null) {
					combatHudManager.onCombatTag(player);
				}
			}
			return true; // Allow damage
		});
		
		// Register entity death event — clear combat tags when attackers die
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			// Notify combat tag manager for ALL entity deaths (mobs, players, etc.)
			// This clears combat tags for any player who was tagged by this entity
			combatTagManager.onEntityDeath(entity.getUuid());

			if (entity instanceof ServerPlayerEntity player) {
				deathTracker.recordDeath(player);
				combatTagManager.clearCombatTag(player); // Clear the dead player's own tags too
				if (combatHudManager != null) {
					combatHudManager.onPlayerDisconnect(player.getUuid()); // Clean up HUD state
				}
				LOGGER.info("Player {} died at {}, recording location", 
					player.getName().getString(), 
					new Vec3d(player.getX(), player.getY(), player.getZ()));
			}
		});

		// Register player disconnect event — combat log punishment
		ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> {
			ServerPlayerEntity player = handler.player;
			if (combatLogHandler != null) {
				combatLogHandler.onPlayerDisconnect(player);
			}
			if (combatHudManager != null) {
				combatHudManager.onPlayerDisconnect(player.getUuid());
			}
		});
		
		// Register chat message event
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			String content = message.getContent().getString();
			if (chatHandler != null) {
				chatHandler.handleChatMessage(sender, content);
			}
			// Bernard listens to ALL chat — records history and replies when mentioned
			if (bernardChatHandler != null) {
				bernardChatHandler.onChatMessage(sender, content);
			}
			// "mb all" — forgive nearby players' combat tags
			if (content.trim().equalsIgnoreCase("mb all")) {
				handleMbAll(sender);
			}
		});
		
		LOGGER.info("Gemini TP Mod initialized successfully");
	}
	
	public static MinecraftServer getServer() {
		return server;
	}
	
	public static DeathLocationTracker getDeathTracker() {
		return deathTracker;
	}
	
	public static GeminiChatHandler getChatHandler() {
		return chatHandler;
	}
	
	public static CombatTagManager getCombatTagManager() {
		return combatTagManager;
	}

	public static CombatHudManager getCombatHudManager() {
		return combatHudManager;
	}

	public static CombatDisplayConfig getCombatDisplayConfig() {
		return combatDisplayConfig;
	}

	public static BegBonus getBegBonus() {
		return begBonus;
	}
	
	public static TsundereManager getTsundereManager() {
		return tsundereManager;
	}
	
	public static ConversationHistory getConversationHistory() {
		return conversationHistory;
	}
	
	public static PersonalityConfig getPersonalityConfig() {
		return personalityConfig;
	}

	/**
	 * "mb all" — when a player says this, clear the PLAYER combat tag
	 * from all other players within 10 blocks. Only removes player-caused
	 * tags, leaving mob/environment tags intact.
	 */
	private static void handleMbAll(net.minecraft.server.network.ServerPlayerEntity sender) {
		String senderName = sender.getName().getString();
		double radius = 10.0;
		int cleared = 0;

		for (net.minecraft.server.network.ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
			if (target.getUuid().equals(sender.getUuid())) continue; // Don't clear own tag
			if (target.squaredDistanceTo(sender) > radius * radius) continue; // Outside 10 blocks

			if (combatTagManager.clearPlayerCombatTag(target)) {
				cleared++;
				target.sendMessage(net.minecraft.text.Text.literal(
					"§a" + senderName + " said mb — your player combat tag has been cleared."));
			}
		}

		if (cleared > 0) {
			sender.sendMessage(net.minecraft.text.Text.literal(
				"§aCleared player combat tags for " + cleared + " nearby player" + (cleared > 1 ? "s" : "") + "."));
		} else {
			sender.sendMessage(net.minecraft.text.Text.literal(
				"§7No nearby players with player combat tags to clear."));
		}
	}
}

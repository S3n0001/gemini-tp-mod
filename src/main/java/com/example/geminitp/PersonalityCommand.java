package com.example.geminitp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Per-player AI personality command.
 * Usage:
 *   /bernardpersonality <player> kind     - Set player to kind mode
 *   /bernardpersonality <player> dickhead - Set player to dickhead mode
 *   /bernardpersonality <player> yandere  - Set player to yandere mode
 *   /bernardpersonality <player> toggle   - Cycle player's mode
 *   /bernardpersonality <player>          - Show player's current mode
 */
public class PersonalityCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
	                            CommandRegistryAccess registryAccess,
	                            CommandManager.RegistrationEnvironment environment,
	                            PersonalityConfig personalityConfig,
	                            ConversationHistory conversationHistory) {
		dispatcher.register(CommandManager.literal("bernardpersonality")
			.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
			.then(CommandManager.argument("player", StringArgumentType.word())
				// No sub-arg — show current mode for player
				.executes(context -> showCurrentMode(context, personalityConfig))
				.then(CommandManager.literal("kind")
					.executes(context -> setMode(context, personalityConfig, conversationHistory, PersonalityConfig.PersonalityMode.KIND)))
				.then(CommandManager.literal("dickhead")
					.executes(context -> setMode(context, personalityConfig, conversationHistory, PersonalityConfig.PersonalityMode.DICKHEAD)))
				.then(CommandManager.literal("yandere")
					.executes(context -> setMode(context, personalityConfig, conversationHistory, PersonalityConfig.PersonalityMode.YANDERE)))
				.then(CommandManager.literal("toggle")
					.executes(context -> toggleMode(context, personalityConfig, conversationHistory)))));
	}

	private static int showCurrentMode(CommandContext<ServerCommandSource> context, PersonalityConfig config) {
		String targetName = StringArgumentType.getString(context, "player");
		String currentMode;
		String description;
		switch (config.getMode(targetName)) {
			case DICKHEAD -> { currentMode = "§cDICKHEAD"; description = "(dismissive, minimal responses)"; }
			case YANDERE -> { currentMode = "§dYANDERE"; description = "(obsessive, possessive, comedic)"; }
			default -> { currentMode = "§aKIND"; description = "(friendly, helpful responses)"; }
		}
		final String mode = currentMode;
		final String desc = description;
		context.getSource().sendFeedback(
			() -> Text.literal("§eBernard's personality for §f" + targetName + "§e: " + mode + " §7" + desc),
			false
		);
		return 1;
	}

	private static int setMode(CommandContext<ServerCommandSource> context,
	                           PersonalityConfig config,
	                           ConversationHistory history,
	                           PersonalityConfig.PersonalityMode mode) {
		String targetName = StringArgumentType.getString(context, "player");

		if (config.getMode(targetName) == mode) {
			String modeName = mode.name();
			context.getSource().sendFeedback(
				() -> Text.literal("§eBernard is already in §f" + modeName + " §emode for " + targetName + "!"),
				false
			);
			return 0;
		}

		config.setMode(targetName, mode);
		clearPlayerConversation(context, history, targetName);

		String colorPrefix = switch (mode) {
			case DICKHEAD -> "§c";
			case YANDERE -> "§d";
			default -> "§a";
		};
		String modeName = switch (mode) {
			case DICKHEAD -> "§4DICKHEAD";
			case YANDERE -> "§5YANDERE";
			default -> "§2KIND";
		};

		context.getSource().sendFeedback(
			() -> Text.literal(colorPrefix + "Bernard is now in " + modeName + colorPrefix + " mode for §f" + targetName + colorPrefix + "!"),
			true
		);
		return 1;
	}

	private static int toggleMode(CommandContext<ServerCommandSource> context,
	                              PersonalityConfig config,
	                              ConversationHistory history) {
		String targetName = StringArgumentType.getString(context, "player");
		config.toggleMode(targetName);
		clearPlayerConversation(context, history, targetName);

		String newMode = switch (config.getMode(targetName)) {
			case DICKHEAD -> "§4DICKHEAD";
			case YANDERE -> "§5YANDERE";
			default -> "§2KIND";
		};
		String colorPrefix = switch (config.getMode(targetName)) {
			case DICKHEAD -> "§c";
			case YANDERE -> "§d";
			default -> "§a";
		};

		context.getSource().sendFeedback(
			() -> Text.literal(colorPrefix + "Bernard's personality for §f" + targetName + colorPrefix + " toggled to " + newMode + colorPrefix + " mode!"),
			true
		);
		return 1;
	}

	private static void clearPlayerConversation(CommandContext<ServerCommandSource> context,
	                                            ConversationHistory history,
	                                            String targetName) {
		ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
		if (target != null) {
			history.clearHistory(target);
			target.sendMessage(Text.literal("§e[Bernard] My personality has changed for you! Starting fresh conversation."));
		}
	}
}

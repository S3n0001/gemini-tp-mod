package com.example.geminitp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BernardCommand {
	
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
	                            CommandRegistryAccess registryAccess,
	                            CommandManager.RegistrationEnvironment environment,
	                            ConversationHistory conversationHistory) {
		dispatcher.register(CommandManager.literal("bernard")
			.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
			.then(CommandManager.literal("reset")
				.then(CommandManager.argument("username", StringArgumentType.string())
					.executes(context -> resetConversation(context, conversationHistory)))));
	}
	
	private static int resetConversation(CommandContext<ServerCommandSource> context, ConversationHistory history) {
		String username = StringArgumentType.getString(context, "username");
		ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(username);
		
		if (target == null) {
			context.getSource().sendError(Text.literal("§cPlayer not found: " + username));
			return 0;
		}
		
		history.clearHistory(target);
		context.getSource().sendFeedback(
			() -> Text.literal("§aCleared conversation history for " + username),
			true
		);
		
		// Notify the player
		target.sendMessage(Text.literal("§aYour conversation history with Bernard has been reset by an admin."));
		
		return 1;
	}
}

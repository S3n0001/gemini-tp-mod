package com.example.geminitp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class TsundereCommand {
	
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
	                            CommandRegistryAccess registryAccess,
	                            CommandManager.RegistrationEnvironment environment,
	                            TsundereManager tsundereManager,
	                            ConversationHistory conversationHistory) {
		dispatcher.register(CommandManager.literal("bernardtsundere")
			.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
			.then(CommandManager.literal("add")
				.then(CommandManager.argument("username", StringArgumentType.string())
					.executes(context -> addUser(context, tsundereManager, conversationHistory))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("username", StringArgumentType.string())
					.executes(context -> removeUser(context, tsundereManager, conversationHistory))))
			.then(CommandManager.literal("list")
				.executes(context -> listUsers(context, tsundereManager))));
	}
	
	private static int addUser(CommandContext<ServerCommandSource> context, TsundereManager manager, ConversationHistory history) {
		String username = StringArgumentType.getString(context, "username");
		
		if (manager.addUser(username)) {
			// Clear conversation history when persona changes
			var player = context.getSource().getServer().getPlayerManager().getPlayer(username);
			if (player != null) {
				history.clearHistory(player);
				player.sendMessage(Text.literal("§e[Bernard] Your conversation history has been reset due to persona change."));
			}
			
			context.getSource().sendFeedback(
				() -> Text.literal("§aAdded " + username + " to tsundere mode! (Conversation history cleared)"),
				true
			);
			return 1;
		} else {
			context.getSource().sendError(Text.literal("§c" + username + " is already in tsundere mode!"));
			return 0;
		}
	}
	
	private static int removeUser(CommandContext<ServerCommandSource> context, TsundereManager manager, ConversationHistory history) {
		String username = StringArgumentType.getString(context, "username");
		
		if (manager.removeUser(username)) {
			// Clear conversation history when persona changes
			var player = context.getSource().getServer().getPlayerManager().getPlayer(username);
			if (player != null) {
				history.clearHistory(player);
				player.sendMessage(Text.literal("§e[Bernard] Your conversation history has been reset due to persona change."));
			}
			
			context.getSource().sendFeedback(
				() -> Text.literal("§aRemoved " + username + " from tsundere mode! (Conversation history cleared)"),
				true
			);
			return 1;
		} else {
			context.getSource().sendError(Text.literal("§c" + username + " is not in tsundere mode!"));
			return 0;
		}
	}
	
	private static int listUsers(CommandContext<ServerCommandSource> context, TsundereManager manager) {
		var users = manager.getTsundereUsers();
		
		if (users.isEmpty()) {
			context.getSource().sendFeedback(
				() -> Text.literal("§eNo users in tsundere mode."),
				false
			);
		} else {
			context.getSource().sendFeedback(
				() -> Text.literal("§aTsundere mode users: " + String.join(", ", users)),
				false
			);
		}
		return 1;
	}
}

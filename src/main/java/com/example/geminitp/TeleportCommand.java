package com.example.geminitp;



import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.arguments.StringArgumentType;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

import net.minecraft.server.command.ServerCommandSource;

import net.minecraft.server.command.CommandManager;

import net.minecraft.text.Text;

import net.minecraft.server.network.ServerPlayerEntity;



import java.util.List;

import java.util.Map;

import java.util.UUID;



public class TeleportCommand {

	

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

		dispatcher.register(

			CommandManager.literal("tpconfirm")

				.then(CommandManager.argument("response", StringArgumentType.word())

					.then(CommandManager.argument("targetPlayer", StringArgumentType.word())

						.executes(context -> {

							String response = StringArgumentType.getString(context, "response");

							ServerCommandSource source = context.getSource();

							

							if (!(source.getEntity() instanceof ServerPlayerEntity player)) {

								return 0;

							}

							

							GeminiChatHandler chatHandler = GeminiTpMod.getChatHandler();

							if (chatHandler == null) {

								player.sendMessage(Text.literal("§cChat handler not initialized!"));

								return 0;

							}

							

							if (response.equalsIgnoreCase("accept")) {

								chatHandler.confirmTeleport(player, true);

							} else if (response.equalsIgnoreCase("deny")) {

								chatHandler.confirmTeleport(player, false);

							} else {

								player.sendMessage(Text.literal("§cInvalid response. Use accept or deny."));

							}

							

							return 1;

						})

					)

					.executes(context -> {

						String response = StringArgumentType.getString(context, "response");

						ServerCommandSource source = context.getSource();

						

						if (!(source.getEntity() instanceof ServerPlayerEntity player)) {

							return 0;

						}

						

						GeminiChatHandler chatHandler = GeminiTpMod.getChatHandler();

						if (chatHandler == null) {

							player.sendMessage(Text.literal("§cChat handler not initialized!"));

							return 0;

						}

						

						if (response.equalsIgnoreCase("accept")) {

							chatHandler.confirmTeleport(player, true);

						} else if (response.equalsIgnoreCase("deny")) {

							chatHandler.confirmTeleport(player, false);

						} else {

							player.sendMessage(Text.literal("§cInvalid response. Use accept or deny."));

						}

						

						return 1;

					})

				)

		);

		

		dispatcher.register(

			CommandManager.literal("tpapprove")

				.then(CommandManager.argument("response", StringArgumentType.word())

					.executes(context -> {

						String response = StringArgumentType.getString(context, "response");

						ServerCommandSource source = context.getSource();

						

						if (!(source.getEntity() instanceof ServerPlayerEntity player)) {

							return 0;

						}

						

						GeminiChatHandler chatHandler = GeminiTpMod.getChatHandler();

						if (chatHandler == null) {

							player.sendMessage(Text.literal("§cChat handler not initialized!"));

							return 0;

						}

						

						if (response.equalsIgnoreCase("accept")) {

							chatHandler.handleTeleportApproval(player, true);

						} else if (response.equalsIgnoreCase("deny")) {

							chatHandler.handleTeleportApproval(player, false);

						} else {

							player.sendMessage(Text.literal("§cInvalid response. Use accept or deny."));

						}

						

						return 1;

					})

				)

		);

		

		// AI command - allows private communication with AI

		dispatcher.register(

			CommandManager.literal("ai")

				.then(CommandManager.argument("message", greedyString())

					.executes(context -> {

						String message = StringArgumentType.getString(context, "message");

						ServerCommandSource source = context.getSource();

						

						if (!(source.getEntity() instanceof ServerPlayerEntity player)) {

							return 0;

						}

						

						GeminiChatHandler chatHandler = GeminiTpMod.getChatHandler();

						if (chatHandler == null) {

							player.sendMessage(Text.literal("§cAI not available!"));

							return 0;

						}

						

						// Process the message through the AI handler (same as @ai in chat)

						chatHandler.handleChatMessage(player, "@ai " + message);

						

						return 1;

					})

				)

		);

		

		// Death coordinates command - operator only

		dispatcher.register(

			CommandManager.literal("deathcords")

				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK)) // Requires operator level 2

				.executes(context -> {

					// Show all players' death locations

					return showAllDeathLocations(context.getSource());

				})

				.then(CommandManager.argument("player", StringArgumentType.word())

					.executes(context -> {

						// Show specific player's death location

						String playerName = StringArgumentType.getString(context, "player");

						return showPlayerDeathLocation(context.getSource(), playerName);

					})

				)

		);

	}

	

	private static int showAllDeathLocations(ServerCommandSource source) {

		DeathLocationTracker deathTracker = GeminiTpMod.getDeathTracker();

		if (deathTracker == null) {

			source.sendFeedback(() -> Text.literal("§cDeath tracker not initialized!"), false);

			return 0;

		}

		

		var allHistory = deathTracker.getAllDeathHistory();

		

		if (allHistory.isEmpty()) {

			source.sendFeedback(() -> Text.literal("§eNo death locations recorded."), false);

			return 1;

		}

		

		int totalDeaths = allHistory.values().stream().mapToInt(List::size).sum();

		

		source.sendFeedback(() -> Text.literal("§6=== All Player Death History ==="), false);

		source.sendFeedback(() -> Text.literal("§7Total players: " + allHistory.size() + " | Total deaths: " + totalDeaths), false);

		source.sendFeedback(() -> Text.literal(""), false);

		

		allHistory.forEach((uuid, deathList) -> {

			// Try to get player name from server

			var server = source.getServer();

			var player = server.getPlayerManager().getPlayer(uuid);

			String playerName = player != null ? player.getName().getString() : "Unknown Player";

			

			source.sendFeedback(() -> Text.literal("§e" + playerName + " §7(" + deathList.size() + " deaths)"), false);

			

			// Show last 5 deaths for each player

			int startIndex = Math.max(0, deathList.size() - 5);

			for (int i = startIndex; i < deathList.size(); i++) {

				var deathLoc = deathList.get(i);

				int deathNumber = i + 1;

				

				String status = deathLoc.used ? "§c[USED]" : "§a[AVAILABLE]";

				String coords = String.format("%.2f, %.2f, %.2f", deathLoc.x, deathLoc.y, deathLoc.z);

				String dimension = deathLoc.dimension.replace("minecraft:", "");

				String time = deathLoc.getFormattedTimestamp();

				

				source.sendFeedback(() -> Text.literal(

					String.format("  §7#%d %s §f%s §7in §b%s §8(%s)", 

						deathNumber, status, coords, dimension, time)

				), false);

			}

			

			if (deathList.size() > 5) {

				int hiddenCount = deathList.size() - 5;

				source.sendFeedback(() -> Text.literal("  §8... and " + hiddenCount + " more (use /deathcords " + playerName + " to see all)"), false);

			}

			

			source.sendFeedback(() -> Text.literal(""), false);

		});

		

		source.sendFeedback(() -> Text.literal("§6================================="), false);

		

		return 1;

	}

	

	private static int showPlayerDeathLocation(ServerCommandSource source, String playerName) {

		DeathLocationTracker deathTracker = GeminiTpMod.getDeathTracker();

		if (deathTracker == null) {

			source.sendFeedback(() -> Text.literal("§cDeath tracker not initialized!"), false);

			return 0;

		}

		

		var server = source.getServer();

		

		// Try to find player by name (online or offline)

		ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(playerName);

		UUID foundUuid = null;

		

		if (targetPlayer == null) {

			// Try to find in death history by matching name

			var allHistory = deathTracker.getAllDeathHistory();

			boolean found = false;

			

			for (var entry : allHistory.entrySet()) {

				var player = server.getPlayerManager().getPlayer(entry.getKey());

				if (player != null && player.getName().getString().equalsIgnoreCase(playerName)) {

					targetPlayer = player;

					foundUuid = entry.getKey();

					found = true;

					break;

				}

			}

			

			if (!found) {

				source.sendFeedback(() -> Text.literal("§cPlayer '" + playerName + "' not found or has no death history."), false);

				return 0;

			}

		} else {

			foundUuid = targetPlayer.getUuid();

		}

		

		final UUID targetUuid = foundUuid;

		

		var deathHistory = deathTracker.getDeathHistory(targetUuid);

		

		if (deathHistory.isEmpty()) {

			source.sendFeedback(() -> Text.literal("§eNo death history recorded for " + playerName + "."), false);

			return 1;

		}

		

		source.sendFeedback(() -> Text.literal("§6=== Death History for " + playerName + " ==="), false);

		source.sendFeedback(() -> Text.literal("§7Total deaths: " + deathHistory.size()), false);

		source.sendFeedback(() -> Text.literal("§8UUID: " + targetUuid.toString()), false);

		source.sendFeedback(() -> Text.literal(""), false);

		

		// Show all deaths in reverse order (newest first)

		for (int i = deathHistory.size() - 1; i >= 0; i--) {

			var deathLoc = deathHistory.get(i);

			int deathNumber = i + 1;

			

			String status = deathLoc.used ? "§c[USED]" : "§a[AVAILABLE]";

			String coords = String.format("%.2f, %.2f, %.2f", deathLoc.x, deathLoc.y, deathLoc.z);

			String dimension = deathLoc.dimension.replace("minecraft:", "");

			String time = deathLoc.getFormattedTimestamp();

			

			source.sendFeedback(() -> Text.literal(

				String.format("§7#%d %s", deathNumber, status)

			), false);

			source.sendFeedback(() -> Text.literal("  §7Coords: §f" + coords), false);

			source.sendFeedback(() -> Text.literal("  §7Dimension: §b" + dimension), false);

			source.sendFeedback(() -> Text.literal("  §8Time: " + time), false);

			source.sendFeedback(() -> Text.literal(""), false);

		}

		

		source.sendFeedback(() -> Text.literal("§6================================="), false);

		

		return 1;

	}

}


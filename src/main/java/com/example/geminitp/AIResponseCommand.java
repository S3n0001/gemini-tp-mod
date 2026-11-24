package com.example.geminitp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Command to handle AI response button clicks
 */
public class AIResponseCommand {
	
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("airesponse")
			.then(CommandManager.argument("clickId", StringArgumentType.string())
				.then(CommandManager.argument("action", StringArgumentType.string())
					.executes(context -> executeResponse(context))
				)
			)
		);
	}
	
	private static int executeResponse(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		
		if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
			return 0;
		}
		
		String messageId = StringArgumentType.getString(context, "clickId");
		String action = StringArgumentType.getString(context, "action");
		
		// Allow any alphanumeric action string (custom actions from rich buttons)
		if (action == null || action.isEmpty() || !action.matches("[\\w]+")) {
			return 0;
		}
		
		// Get chat handler
		GeminiChatHandler chatHandler = GeminiTpMod.getChatHandler();
		if (chatHandler == null) {
			return 0;
		}
		
		// Get parser and handle click
		ClickableTextParser parser = chatHandler.getClickableTextParser();
		if (parser == null) {
			return 0;
		}
		
		boolean processed = parser.handleClick(messageId, action, player);
		
		if (!processed) {
			// Silently ignore if already clicked
			return 0;
		}
		
		// Don't send any feedback - just let the AI respond
		
		return 1;
	}
}

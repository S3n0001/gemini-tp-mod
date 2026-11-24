package com.example.geminitp;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich interactive text parser for AI messages.
 * 
 * Supports:
 * - [ACCEPT] / [DENY] — classic green/red buttons
 * - ["Custom Text"=ACCEPT] / ["Custom Text"=DENY] — custom label, classic action
 * - [!"Custom Text"=custom_action] — fully custom button with custom action string
 * - [!"Text"=action color:gold] — custom button with explicit color
 * - [!"Text"=action color:red hover:Tooltip text here] — custom button with color + hover
 * - {---} or {===} — horizontal separator/divider lines
 * - {bar:7/10 color:green} — progress bar
 * - {header:Section Title} — styled section header
 */
public class ClickableTextParser {
	// === BUTTON PATTERNS ===
	// Fully custom button: [!"text"=action] or [!"text"=action color:x] or [!"text"=action color:x hover:text]
	private static final Pattern RICH_BUTTON_PATTERN = Pattern.compile(
		"\\[![\"\u201C]([^\"\u201D]+)[\"\u201D]=([\\w]+)(?:\\s+color:(\\w+))?(?:\\s+hover:([^\\]]+))?\\]"
	);
	// Custom text with ACCEPT/DENY: ["text"=ACCEPT] or ["text"=DENY]
	private static final Pattern CUSTOM_PATTERN = Pattern.compile(
		"\\[[\"\u201C]([^\"\u201D]+)[\"\u201D]=(ACCEPT|DENY)\\]"
	);
	// Simple [ACCEPT] or [DENY]
	private static final Pattern SIMPLE_PATTERN = Pattern.compile("\\[(ACCEPT|DENY)\\]");
	
	// === DECORATIVE PATTERNS ===
	// Separator: {---} or {===} or {~~~} with optional color
	private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\{(---+|===+|~~~+)(?:\\s+color:(\\w+))?\\}");
	// Progress bar: {bar:3/10} or {bar:3/10 color:green}
	private static final Pattern PROGRESS_BAR_PATTERN = Pattern.compile("\\{bar:(\\d+)/(\\d+)(?:\\s+color:(\\w+))?\\}");
	// Header: {header:Title Text} or {header:Title Text color:gold}
	private static final Pattern HEADER_PATTERN = Pattern.compile("\\{header:([^}]+?)(?:\\s+color:(\\w+))?\\}");

	// Track which interactive messages have been clicked to prevent multiple clicks
	private final Map<String, Boolean> clickedMessages = new ConcurrentHashMap<>();
	private final GeminiChatHandler chatHandler;
	
	public ClickableTextParser(GeminiChatHandler chatHandler) {
		this.chatHandler = chatHandler;
	}
	
	/**
	 * Parse a message and return a Text component with clickable elements and rich formatting
	 */
	public MutableText parseMessage(ServerPlayerEntity player, String message, String messageId) {
		// Collect all "tokens" (buttons, decorations, plain text) sorted by position
		List<Token> tokens = new ArrayList<>();
		
		// Find rich custom buttons [!"text"=action ...]
		findMatches(RICH_BUTTON_PATTERN, message, tokens, TokenType.RICH_BUTTON);
		// Find custom ACCEPT/DENY buttons ["text"=ACTION]
		findMatches(CUSTOM_PATTERN, message, tokens, TokenType.CUSTOM_BUTTON);
		// Find simple [ACCEPT]/[DENY]
		findMatches(SIMPLE_PATTERN, message, tokens, TokenType.SIMPLE_BUTTON);
		// Find separators {---}
		findMatches(SEPARATOR_PATTERN, message, tokens, TokenType.SEPARATOR);
		// Find progress bars {bar:x/y}
		findMatches(PROGRESS_BAR_PATTERN, message, tokens, TokenType.PROGRESS_BAR);
		// Find headers {header:text}
		findMatches(HEADER_PATTERN, message, tokens, TokenType.HEADER);
		
		// Remove overlapping tokens (rich buttons take priority over custom/simple)
		tokens = removeOverlaps(tokens);
		
		if (tokens.isEmpty()) {
			return Text.literal(message);
		}
		
		// Sort by position
		tokens.sort(Comparator.comparingInt(t -> t.start));
		
		// Build the final Text component
		MutableText result = Text.literal("");
		int lastIndex = 0;
		
		for (Token token : tokens) {
			// Add plain text before this token
			if (lastIndex < token.start) {
				result.append(Text.literal(message.substring(lastIndex, token.start)));
			}
			
			// Render the token
			switch (token.type) {
				case RICH_BUTTON -> result.append(renderRichButton(player, token, messageId));
				case CUSTOM_BUTTON -> result.append(renderCustomButton(player, token, messageId));
				case SIMPLE_BUTTON -> result.append(renderSimpleButton(player, token, messageId));
				case SEPARATOR -> result.append(renderSeparator(token));
				case PROGRESS_BAR -> result.append(renderProgressBar(token));
				case HEADER -> result.append(renderHeader(token));
			}
			
			lastIndex = token.end;
		}
		
		// Add remaining text
		if (lastIndex < message.length()) {
			result.append(Text.literal(message.substring(lastIndex)));
		}
		
		return result;
	}
	
	// ==================== RENDERERS ====================
	
	private MutableText renderRichButton(ServerPlayerEntity player, Token token, String messageId) {
		Matcher m = RICH_BUTTON_PATTERN.matcher(token.raw);
		m.find();
		String displayText = m.group(1);
		String action = m.group(2);
		String colorName = m.group(3);
		String hoverText = m.group(4);
		
		Formatting color = parseColor(colorName, action.equals("ACCEPT") ? Formatting.GREEN : 
		                                          action.equals("DENY") ? Formatting.RED : Formatting.AQUA);
		String hover = hoverText != null ? hoverText.trim() : "Click to select: " + displayText;
		
		return Text.literal(" [" + displayText + "] ").styled(style -> style
			.withClickEvent(new ClickEvent.RunCommand("/airesponse " + messageId + " " + action))
			.withHoverEvent(new HoverEvent.ShowText(Text.literal(hover).formatted(Formatting.GRAY)))
			.withFormatting(color)
			.withFormatting(Formatting.BOLD));
	}
	
	private MutableText renderCustomButton(ServerPlayerEntity player, Token token, String messageId) {
		Matcher m = CUSTOM_PATTERN.matcher(token.raw);
		m.find();
		String displayText = m.group(1);
		String action = m.group(2);
		Formatting color = action.equals("ACCEPT") ? Formatting.GREEN : Formatting.RED;
		
		return Text.literal(" [" + displayText + "] ").styled(style -> style
			.withClickEvent(new ClickEvent.RunCommand("/airesponse " + messageId + " " + action))
			.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to " + action.toLowerCase()).formatted(Formatting.GRAY)))
			.withFormatting(color)
			.withFormatting(Formatting.BOLD));
	}
	
	private MutableText renderSimpleButton(ServerPlayerEntity player, Token token, String messageId) {
		Matcher m = SIMPLE_PATTERN.matcher(token.raw);
		m.find();
		String action = m.group(1);
		Formatting color = action.equals("ACCEPT") ? Formatting.GREEN : Formatting.RED;
		
		return Text.literal(" [" + action + "] ").styled(style -> style
			.withClickEvent(new ClickEvent.RunCommand("/airesponse " + messageId + " " + action))
			.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to " + action.toLowerCase()).formatted(Formatting.GRAY)))
			.withFormatting(color)
			.withFormatting(Formatting.BOLD));
	}

	private MutableText renderSeparator(Token token) {
		Matcher m = SEPARATOR_PATTERN.matcher(token.raw);
		m.find();
		String chars = m.group(1);
		String colorName = m.group(2);
		
		char sepChar = chars.charAt(0);
		String line;
		if (sepChar == '=') {
			line = "═══════════════════════════════";
		} else if (sepChar == '~') {
			line = "~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~";
		} else {
			line = "───────────────────────────────";
		}
		
		Formatting color = parseColor(colorName, Formatting.DARK_GRAY);
		return Text.literal("\n" + line + "\n").formatted(color);
	}
	
	private MutableText renderProgressBar(Token token) {
		Matcher m = PROGRESS_BAR_PATTERN.matcher(token.raw);
		m.find();
		int current = Integer.parseInt(m.group(1));
		int max = Integer.parseInt(m.group(2));
		String colorName = m.group(3);
		
		current = Math.max(0, Math.min(current, max));
		max = Math.max(1, max);
		
		int filledBlocks = (int) Math.round((double) current / max * 20);
		int emptyBlocks = 20 - filledBlocks;
		
		Formatting filledColor = parseColor(colorName, Formatting.GREEN);
		
		MutableText bar = Text.literal("[");
		bar.append(Text.literal("█".repeat(filledBlocks)).formatted(filledColor));
		bar.append(Text.literal("░".repeat(emptyBlocks)).formatted(Formatting.DARK_GRAY));
		bar.append(Text.literal("] " + current + "/" + max));
		
		return bar;
	}
	
	private MutableText renderHeader(Token token) {
		Matcher m = HEADER_PATTERN.matcher(token.raw);
		m.find();
		String title = m.group(1).trim();
		String colorName = m.group(2);
		
		Formatting color = parseColor(colorName, Formatting.GOLD);
		
		MutableText header = Text.literal("");
		header.append(Text.literal("▸ ").formatted(color));
		header.append(Text.literal(title).styled(style -> style.withFormatting(color).withFormatting(Formatting.BOLD)));
		
		return header;
	}
	
	// ==================== UTILITIES ====================
	
	private void findMatches(Pattern pattern, String message, List<Token> tokens, TokenType type) {
		Matcher matcher = pattern.matcher(message);
		while (matcher.find()) {
			tokens.add(new Token(matcher.start(), matcher.end(), matcher.group(), type));
		}
	}
	
	/**
	 * Remove overlapping tokens. Priority: RICH_BUTTON > CUSTOM_BUTTON > SIMPLE_BUTTON > decorations
	 */
	private List<Token> removeOverlaps(List<Token> tokens) {
		tokens.sort(Comparator.<Token, Integer>comparing(t -> t.type.ordinal()).thenComparingInt(t -> t.start));
		List<Token> result = new ArrayList<>();
		
		for (Token candidate : tokens) {
			boolean overlaps = false;
			for (Token existing : result) {
				if (candidate.start < existing.end && candidate.end > existing.start) {
					overlaps = true;
					break;
				}
			}
			if (!overlaps) {
				result.add(candidate);
			}
		}
		return result;
	}
	
	private Formatting parseColor(String colorName, Formatting fallback) {
		if (colorName == null) return fallback;
		return switch (colorName.toLowerCase()) {
			case "red" -> Formatting.RED;
			case "green" -> Formatting.GREEN;
			case "blue" -> Formatting.BLUE;
			case "aqua", "cyan" -> Formatting.AQUA;
			case "gold", "orange" -> Formatting.GOLD;
			case "yellow" -> Formatting.YELLOW;
			case "purple", "magenta" -> Formatting.LIGHT_PURPLE;
			case "dark_purple" -> Formatting.DARK_PURPLE;
			case "pink" -> Formatting.LIGHT_PURPLE;
			case "white" -> Formatting.WHITE;
			case "gray", "grey" -> Formatting.GRAY;
			case "dark_gray" -> Formatting.DARK_GRAY;
			case "dark_red" -> Formatting.DARK_RED;
			case "dark_green" -> Formatting.DARK_GREEN;
			case "dark_blue" -> Formatting.DARK_BLUE;
			case "dark_aqua" -> Formatting.DARK_AQUA;
			default -> fallback;
		};
	}
	
	// ==================== CLICK HANDLING ====================
	
	/**
	 * Handle a button click
	 * @return true if the click was valid and processed, false if already clicked
	 */
	public boolean handleClick(String messageId, String action, ServerPlayerEntity player) {
		if (clickedMessages.containsKey(messageId)) {
			return false;
		}
		clickedMessages.put(messageId, true);
		chatHandler.handleAIButtonResponse(player, action);
		return true;
	}
	
	public boolean isClicked(String messageId) {
		return clickedMessages.containsKey(messageId);
	}
	
	public void clearOldClicks() {
		// Could add timestamp-based cleanup here
	}
	
	// ==================== INNER TYPES ====================
	
	private enum TokenType {
		RICH_BUTTON,    // highest priority
		CUSTOM_BUTTON,
		SIMPLE_BUTTON,
		SEPARATOR,
		PROGRESS_BAR,
		HEADER
	}
	
	private static class Token {
		final int start;
		final int end;
		final String raw;
		final TokenType type;
		
		Token(int start, int end, String raw, TokenType type) {
			this.start = start;
			this.end = end;
			this.raw = raw;
			this.type = type;
		}
	}
}

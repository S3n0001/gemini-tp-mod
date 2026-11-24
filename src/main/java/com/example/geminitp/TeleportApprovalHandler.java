package com.example.geminitp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TeleportApprovalHandler {
	private final MinecraftServer server;
	private final CombatTagManager combatTagManager;
	private final RateLimiter rateLimiter;
	private final TeleportRequest teleportRequest;
	private final TeleportExecutor teleportExecutor;
	
	public TeleportApprovalHandler(MinecraftServer server, CombatTagManager combatTagManager,
	                               RateLimiter rateLimiter, TeleportRequest teleportRequest,
	                               TeleportExecutor teleportExecutor) {
		this.server = server;
		this.combatTagManager = combatTagManager;
		this.rateLimiter = rateLimiter;
		this.teleportRequest = teleportRequest;
		this.teleportExecutor = teleportExecutor;
	}
	
	public void handleTeleportApproval(ServerPlayerEntity targetPlayer, boolean approved) {
		TeleportRequest.PendingRequest request = teleportRequest.getOldestPendingRequest(targetPlayer);
		
		if (request == null) {
			sendMessage(targetPlayer, "§cNo pending teleport requests");
			return;
		}
		
		ServerPlayerEntity requester = server.getPlayerManager().getPlayer(request.requesterId);
		teleportRequest.removePendingRequest(targetPlayer, request.requesterId);
		
		int remainingRequests = teleportRequest.getPendingRequestCount(targetPlayer);
		
		if (!approved) {
			sendMessage(targetPlayer, "§eTeleport request from " + request.requesterName + " denied");
			if (remainingRequests > 0) {
				TeleportRequest.PendingRequest nextRequest = teleportRequest.getOldestPendingRequest(targetPlayer);
				if (nextRequest != null) {
					sendClickableApprovalMessage(targetPlayer, nextRequest.requesterName, remainingRequests);
				}
			}
			return;
		}
		
		if (requester == null) {
			sendMessage(targetPlayer, "§c" + request.requesterName + " is no longer online");
			if (remainingRequests > 0) {
				TeleportRequest.PendingRequest nextRequest = teleportRequest.getOldestPendingRequest(targetPlayer);
				if (nextRequest != null) {
					sendClickableApprovalMessage(targetPlayer, nextRequest.requesterName, remainingRequests);
				}
			}
			return;
		}
		
		if (combatTagManager.isInCombat(requester)) {
			long remaining = combatTagManager.getRemainingCombatTime(requester);
			sendMessage(requester, "§cYour teleport was approved but you are in combat! (" + (remaining / 1000) + "s remaining)");
			sendMessage(targetPlayer, "§c" + request.requesterName + " is in combat and cannot teleport");
			if (remainingRequests > 0) {
				TeleportRequest.PendingRequest nextRequest = teleportRequest.getOldestPendingRequest(targetPlayer);
				if (nextRequest != null) {
					sendClickableApprovalMessage(targetPlayer, nextRequest.requesterName, remainingRequests);
				}
			}
			return;
		}
		
		if (combatTagManager.isInCombat(targetPlayer)) {
			long remaining = combatTagManager.getRemainingCombatTime(targetPlayer);
			sendMessage(targetPlayer, "§cYou cannot approve teleports while in combat! (" + (remaining / 1000) + "s remaining)");
			sendMessage(requester, "§c" + targetPlayer.getName().getString() + " is in combat and cannot approve your request");
			teleportRequest.createRequest(requester, targetPlayer);
			return;
		}
		
		// Create confirmation and send popup to requester
		teleportRequest.createConfirmation(requester, targetPlayer);
		sendMessage(targetPlayer, "§aTeleport request approved.");
		sendRequesterConfirmationMessage(requester, targetPlayer);
		
		if (remainingRequests > 0) {
			TeleportRequest.PendingRequest nextRequest = teleportRequest.getOldestPendingRequest(targetPlayer);
			if (nextRequest != null) {
				sendClickableApprovalMessage(targetPlayer, nextRequest.requesterName, remainingRequests);
			}
		}
	}
	
	private void sendClickableApprovalMessage(ServerPlayerEntity target, String requesterName, int queuePosition) {
		server.execute(() -> {
			MutableText message = Text.literal("§e" + requesterName + " wants to teleport to you. ");
			
			MutableText acceptButton = Text.literal("§a[ACCEPT]")
				.styled(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/tpapprove accept"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to accept")))
					.withFormatting(Formatting.GREEN).withFormatting(Formatting.BOLD));
			
			message.append(acceptButton);
			message.append(Text.literal(" "));
			
			MutableText denyButton = Text.literal("§c[DENY]")
				.styled(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/tpapprove deny"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to deny")))
					.withFormatting(Formatting.RED).withFormatting(Formatting.BOLD));
			
			message.append(denyButton);
			
			target.sendMessage(message);
			
			if (queuePosition > 1) {
				target.sendMessage(Text.literal("§7You have " + queuePosition + " pending teleport requests"));
			}
		});
	}
	
	private void sendRequesterConfirmationMessage(ServerPlayerEntity requester, ServerPlayerEntity targetPlayer) {
		server.execute(() -> {
			MutableText message = Text.literal(
				"§e" + targetPlayer.getName().getString() + " approved your request! Would you like to teleport to them now? "
			);
			
			MutableText acceptButton = Text.literal("§a[ACCEPT]")
				.styled(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/tpconfirm accept " + targetPlayer.getName().getString()))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to teleport")))
					.withFormatting(Formatting.GREEN).withFormatting(Formatting.BOLD));
			
			message.append(acceptButton);
			message.append(Text.literal(" "));
			
			MutableText denyButton = Text.literal("§c[DENY]")
				.styled(style -> style
					.withClickEvent(new ClickEvent.RunCommand("/tpconfirm deny"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to cancel")))
					.withFormatting(Formatting.RED).withFormatting(Formatting.BOLD));
			
			message.append(denyButton);
			
			requester.sendMessage(message);
		});
	}
	
	private void sendMessage(ServerPlayerEntity player, String message) {
		server.execute(() -> player.sendMessage(Text.literal(message)));
	}
}

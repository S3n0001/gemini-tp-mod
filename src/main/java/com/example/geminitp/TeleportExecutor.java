package com.example.geminitp;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class TeleportExecutor {
	private final MinecraftServer server;
	
	public TeleportExecutor(MinecraftServer server) {
		this.server = server;
	}
	
	public void executeTeleport(ServerPlayerEntity player, double x, double y, double z, String dimensionStr) {
		server.execute(() -> {
			try {
				Identifier dimLocation = Identifier.of(dimensionStr);
				RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimLocation);
				ServerWorld targetLevel = server.getWorld(dimensionKey);
				
				if (targetLevel == null) {
					sendMessage(player, "§cInvalid dimension: " + dimensionStr);
					return;
				}
				
				if (player.getCommandSource().getWorld() != targetLevel) {
					player.teleport(targetLevel, x, y, z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), false);
				} else {
					// Load the chunk before teleporting to ensure it exists
					targetLevel.getChunk((int) x >> 4, (int) z >> 4);
					player.teleport(targetLevel, x, y, z, java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), false);
				}
				
				// Grant 3 seconds of anticheat immunity after Gemini TP.
				// EntityTeleportMixin sets 20 grace ticks (1s) for all teleports,
				// but Gemini TPs can be cross-dimension so we need a longer window.
				grantAnticheatImmunity(player);
			} catch (Exception e) {
				GeminiTpMod.LOGGER.error("Error executing teleport", e);
				sendMessage(player, "§cTeleport failed");
			}
		});
	}
	
	/**
	 * Sets the anticheat grace period to 3 seconds (60 ticks) for Gemini TPs.
	 * Uses reflection since the anticheat is a separate mod.
	 */
	private void grantAnticheatImmunity(ServerPlayerEntity player) {
		try {
			Class<?> moveDataClass = Class.forName("com.antiexploit.data.PlayerMoveData");
			java.lang.reflect.Method getOrCreate = moveDataClass.getMethod("getOrCreate", java.util.UUID.class);
			Object data = getOrCreate.invoke(null, player.getUuid());
			
			java.lang.reflect.Field teleported = moveDataClass.getField("teleported");
			teleported.setBoolean(data, true);
			
			java.lang.reflect.Field graceTicks = moveDataClass.getField("teleportGraceTicks");
			graceTicks.setInt(data, 60); // 3 seconds
			
			GeminiTpMod.LOGGER.debug("Granted 3s anticheat immunity to {}", player.getName().getString());
		} catch (ClassNotFoundException e) {
			// Anticheat mod not loaded, nothing to bypass
		} catch (Exception e) {
			GeminiTpMod.LOGGER.warn("Failed to grant anticheat immunity: {}", e.getMessage());
		}
	}
	
	private void sendMessage(ServerPlayerEntity player, String message) {
		server.execute(() -> player.sendMessage(Text.literal(message)));
	}
}

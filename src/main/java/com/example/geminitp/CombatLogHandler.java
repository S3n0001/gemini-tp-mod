package com.example.geminitp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Handles combat logging punishment.
 * 
 * Rules:
 * - PvP combat (PLAYER tag): disconnect = instant death, always
 * - PvE combat (MOB/ENVIRONMENT tag):
 *   - If HP >= 50%: allowed to log safely
 *   - If HP < 50%: disconnect = death
 */
public class CombatLogHandler {

    private final MinecraftServer server;
    private final CombatTagManager combatTagManager;

    public CombatLogHandler(MinecraftServer server, CombatTagManager combatTagManager) {
        this.server = server;
        this.combatTagManager = combatTagManager;
    }

    /**
     * Called when a player disconnects. Returns true if the player should die.
     * Must be called BEFORE the player entity is removed from the world.
     */
    public boolean onPlayerDisconnect(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return false;
        if (!combatTagManager.isInCombat(player)) return false;

        CombatTagManager.CombatTag tag = combatTagManager.getCombatTag(player);
        if (tag == null) return false;

        String playerName = player.getName().getString();
        float healthPercent = player.getHealth() / player.getMaxHealth();

        if ("PLAYER".equals(tag.causeType)) {
            // PvP combat log — always die
            GeminiTpMod.LOGGER.info("Combat log: {} disconnected during PvP combat with {} — killing",
                playerName, tag.causeName);
            killPlayer(player, "§c" + playerName + " combat logged during PvP!");
            return true;
        } else {
            // PvE combat (MOB or ENVIRONMENT)
            if (healthPercent < 0.5f) {
                // Below 50% HP — die
                GeminiTpMod.LOGGER.info("Combat log: {} disconnected during PvE combat at {}% HP — killing",
                    playerName, (int)(healthPercent * 100));
                killPlayer(player, "§c" + playerName + " combat logged at low HP!");
                return true;
            } else {
                // Above 50% HP — safe to log
                GeminiTpMod.LOGGER.info("Combat log: {} disconnected during PvE combat at {}% HP — allowed",
                    playerName, (int)(healthPercent * 100));
                return false;
            }
        }
    }

    private void killPlayer(ServerPlayerEntity player, String broadcastMessage) {
        // Kill the player — their items will drop where they were standing
        player.kill(server.getOverworld());

        // Broadcast to all online players
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(broadcastMessage));
        }

        // Clear their combat tag since they're now dead
        combatTagManager.clearCombatTag(player);
    }
}

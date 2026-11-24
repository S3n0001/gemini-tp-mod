package com.example.geminitp;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages combat tags per player. Supports multiple concurrent tags
 * (player, mob, environment) so they can be cleared independently.
 * 
 * Tags track the attacker's UUID so we can:
 * - Clear tags when the attacker dies (killed by you or anyone else)
 * - Handle player respawns correctly (UUID persists but we check alive state)
 * - Environment tags have no attacker UUID and expire naturally
 */
public class CombatTagManager {
    private static final long PLAYER_COMBAT_DURATION = 60000; // 60 seconds
    private static final long MOB_COMBAT_DURATION = 25000; // 25 seconds
    private static final long ENVIRONMENT_COMBAT_DURATION = 5000; // 5 seconds

    // UUID -> list of active combat tags (can have multiple: player + mob + env)
    private final Map<UUID, List<CombatTag>> combatTags;
    private MinecraftServer server;

    public CombatTagManager() {
        this.combatTags = new ConcurrentHashMap<>();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void tagPlayer(ServerPlayerEntity player, DamageSource source) {
        if (player.isCreative() || player.isSpectator()) return;

        long duration;
        String causeType;
        String causeName;
        UUID attackerUuid = null;

        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            duration = PLAYER_COMBAT_DURATION;
            causeType = "PLAYER";
            causeName = attacker.getName().getString();
            attackerUuid = attacker.getUuid();
        } else if (source.getAttacker() != null) {
            duration = MOB_COMBAT_DURATION;
            causeType = "MOB";
            causeName = source.getAttacker().getType().getName().getString();
            attackerUuid = source.getAttacker().getUuid();
        } else {
            duration = ENVIRONMENT_COMBAT_DURATION;
            causeType = "ENVIRONMENT";
            causeName = source.getName();
            // No attacker UUID for environment damage — expires naturally
        }

        CombatTag tag = new CombatTag(System.currentTimeMillis(), duration, causeType, causeName, attackerUuid);

        List<CombatTag> tags = combatTags.computeIfAbsent(player.getUuid(), k -> Collections.synchronizedList(new ArrayList<>()));

        // Remove expired/invalid tags and any existing tag of the same type (refresh it)
        long now = System.currentTimeMillis();
        tags.removeIf(t -> t.isExpired(now) || t.causeType.equals(causeType));
        tags.add(tag);

        GeminiTpMod.LOGGER.debug("Tagged player {} in combat for {}ms due to {} ({})",
            player.getName().getString(), duration, causeName, causeType);
    }

    /**
     * Called when ANY entity dies (mob, player, etc.).
     * Removes all combat tags across all players that reference this dead entity.
     */
    public void onEntityDeath(UUID deadEntityUuid) {
        if (deadEntityUuid == null) return;

        for (Map.Entry<UUID, List<CombatTag>> entry : combatTags.entrySet()) {
            List<CombatTag> tags = entry.getValue();
            if (tags == null) continue;

            boolean removed = tags.removeIf(t ->
                t.attackerUuid != null && t.attackerUuid.equals(deadEntityUuid));

            if (removed) {
                GeminiTpMod.LOGGER.debug("Cleared combat tag for player {} — attacker {} died",
                    entry.getKey(), deadEntityUuid);
            }

            if (tags.isEmpty()) {
                combatTags.remove(entry.getKey());
            }
        }
    }

    /**
     * Check if a tag's attacker is still alive.
     * Environment tags (no attacker) are always considered valid until they expire.
     * Player tags check if the player is online and alive.
     * Mob tags check if the entity still exists in the world.
     */
    private boolean isAttackerAlive(CombatTag tag) {
        if (tag.attackerUuid == null) return true; // Environment — no attacker to check
        if (server == null) return true; // Can't check without server reference

        // Check if it's a player
        ServerPlayerEntity playerAttacker = server.getPlayerManager().getPlayer(tag.attackerUuid);
        if (playerAttacker != null) {
            // Player is online — check if alive (not dead/respawning)
            return playerAttacker.isAlive();
        }

        // Not a player (or player is offline) — check all worlds for the entity
        for (var world : server.getWorlds()) {
            Entity entity = world.getEntity(tag.attackerUuid);
            if (entity != null) {
                return entity.isAlive();
            }
        }

        // Entity not found in any world — it's dead/despawned
        return false;
    }

    /** Remove expired tags AND tags whose attacker is dead. */
    private void cleanTags(List<CombatTag> tags) {
        long now = System.currentTimeMillis();
        tags.removeIf(t -> t.isExpired(now) || !isAttackerAlive(t));
    }

    public boolean isInCombat(ServerPlayerEntity player) {
        List<CombatTag> tags = combatTags.get(player.getUuid());
        if (tags == null || tags.isEmpty()) return false;

        cleanTags(tags);

        if (tags.isEmpty()) {
            combatTags.remove(player.getUuid());
            return false;
        }
        return true;
    }

    /**
     * Get the most severe active combat tag (PLAYER > MOB > ENVIRONMENT).
     * Returns null if not in combat.
     */
    public CombatTag getCombatTag(ServerPlayerEntity player) {
        List<CombatTag> tags = combatTags.get(player.getUuid());
        if (tags == null || tags.isEmpty()) return null;

        cleanTags(tags);

        if (tags.isEmpty()) {
            combatTags.remove(player.getUuid());
            return null;
        }

        // Return highest priority: PLAYER > MOB > ENVIRONMENT
        CombatTag best = null;
        for (CombatTag t : tags) {
            if (best == null || getPriority(t.causeType) > getPriority(best.causeType)) {
                best = t;
            }
        }
        return best;
    }

    public long getRemainingCombatTime(ServerPlayerEntity player) {
        List<CombatTag> tags = combatTags.get(player.getUuid());
        if (tags == null || tags.isEmpty()) return 0;

        cleanTags(tags);

        if (tags.isEmpty()) {
            combatTags.remove(player.getUuid());
            return 0;
        }

        // Return the longest remaining time across all tags
        long maxRemaining = 0;
        for (CombatTag t : tags) {
            long remaining = t.getRemainingMs(System.currentTimeMillis());
            if (remaining > maxRemaining) maxRemaining = remaining;
        }
        return maxRemaining;
    }

    /** Clear ALL combat tags for a player (used on death, etc.) */
    public void clearCombatTag(ServerPlayerEntity player) {
        combatTags.remove(player.getUuid());
    }

    /**
     * Clear only PLAYER-caused combat tags for a target.
     * Used by "mb all" — forgives player combat but leaves mob/env tags.
     */
    public boolean clearPlayerCombatTag(ServerPlayerEntity target) {
        List<CombatTag> tags = combatTags.get(target.getUuid());
        if (tags == null || tags.isEmpty()) return false;

        cleanTags(tags);

        boolean removed = tags.removeIf(t -> t.causeType.equals("PLAYER"));

        if (tags.isEmpty()) {
            combatTags.remove(target.getUuid());
        }
        return removed;
    }

    /** Check if a player has specifically a PLAYER-caused combat tag. */
    public boolean hasPlayerCombatTag(ServerPlayerEntity player) {
        List<CombatTag> tags = combatTags.get(player.getUuid());
        if (tags == null) return false;

        cleanTags(tags);

        return tags.stream().anyMatch(t -> t.causeType.equals("PLAYER"));
    }

    private int getPriority(String causeType) {
        return switch (causeType) {
            case "PLAYER" -> 3;
            case "MOB" -> 2;
            case "ENVIRONMENT" -> 1;
            default -> 0;
        };
    }

    public static class CombatTag {
        public final long taggedTime;
        public final long duration;
        public final String causeType; // PLAYER, MOB, ENVIRONMENT
        public final String causeName;
        public final UUID attackerUuid; // UUID of the attacker entity (null for environment)

        public CombatTag(long taggedTime, long duration, String causeType, String causeName, UUID attackerUuid) {
            this.taggedTime = taggedTime;
            this.duration = duration;
            this.causeType = causeType;
            this.causeName = causeName;
            this.attackerUuid = attackerUuid;
        }

        public boolean isExpired(long now) {
            return (now - taggedTime) >= duration;
        }

        public long getRemainingMs(long now) {
            long remaining = duration - (now - taggedTime);
            return Math.max(0, remaining);
        }

        public String getFormattedRemaining(long remainingMs) {
            long seconds = remainingMs / 1000;
            return seconds + "s";
        }
    }
}

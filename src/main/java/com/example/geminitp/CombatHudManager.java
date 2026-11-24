package com.example.geminitp;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages the combat HUD overlay shown on the action bar.
 * Deepwoken-style "IN DANGER" display with per-player display modes.
 * 
 * Avoids overlap with ThinkingSpinner — if Bernard is thinking,
 * the combat HUD yields (spinner takes priority since it's brief).
 * Combat HUD resumes once the spinner stops.
 */
public class CombatHudManager {

    private final MinecraftServer server;
    private final CombatTagManager combatTagManager;
    private final CombatDisplayConfig displayConfig;
    private final ScheduledExecutorService scheduler;

    // Track which players currently have an active HUD ticker
    private final Map<UUID, ScheduledFuture<?>> activeHuds = new ConcurrentHashMap<>();
    // Track which players were already in combat (to avoid re-playing sound)
    private final Set<UUID> knownInCombat = ConcurrentHashMap.newKeySet();
    // Track pulse timing per player
    private final Map<UUID, Long> pulseStartTime = new ConcurrentHashMap<>();

    // Reference to the thinking spinner so we can check if it's active
    private ThinkingSpinner thinkingSpinner;

    public CombatHudManager(MinecraftServer server, CombatTagManager combatTagManager,
                            CombatDisplayConfig displayConfig) {
        this.server = server;
        this.combatTagManager = combatTagManager;
        this.displayConfig = displayConfig;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "combat-hud");
            t.setDaemon(true);
            return t;
        });
    }

    public void setThinkingSpinner(ThinkingSpinner spinner) {
        this.thinkingSpinner = spinner;
    }

    /**
     * Called when a player gets tagged in combat.
     * Starts the HUD ticker if not already running, plays wither sound on first tag.
     */
    public void onCombatTag(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        String name = player.getName().getString();

        // Play wither sound on combat ENTRY (not refresh)
        if (!knownInCombat.contains(id)) {
            knownInCombat.add(id);
            if (displayConfig.isSoundEnabled(name)) {
                playCombatSound(player);
            }
        }

        // Start HUD ticker if not already running
        if (!activeHuds.containsKey(id)) {
            startHudTicker(player);
        }
    }

    private void playCombatSound(ServerPlayerEntity player) {
        server.execute(() -> {
            // Play wither ambient sound — ominous and recognizable
            player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                RegistryEntry.of(SoundEvents.ENTITY_WITHER_AMBIENT),
                SoundCategory.MASTER,
                player.getX(), player.getY(), player.getZ(),
                0.5f,  // volume — not too loud
                1.2f,  // pitch — slightly higher for urgency
                java.util.concurrent.ThreadLocalRandom.current().nextLong()
            ));
        });
    }

    private void startHudTicker(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        pulseStartTime.put(id, System.currentTimeMillis());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            server.execute(() -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p == null) {
                    stopHud(id);
                    return;
                }

                // Check if still in combat
                if (!combatTagManager.isInCombat(p)) {
                    // Combat ended — clear HUD and state
                    knownInCombat.remove(id);
                    p.sendMessage(Text.literal(""), true); // clear action bar
                    stopHud(id);
                    return;
                }

                // If ThinkingSpinner is active, yield the action bar
                if (thinkingSpinner != null && isSpinnerActive(id)) {
                    return; // Don't overwrite Bernard's thinking message
                }

                String playerName = p.getName().getString();
                CombatDisplayConfig.DisplayMode mode = displayConfig.getDisplayMode(playerName);

                if (mode == CombatDisplayConfig.DisplayMode.HIDDEN) {
                    return; // Player doesn't want to see it
                }

                // Pulse mode: 3s visible, 5s hidden, repeat
                if (mode == CombatDisplayConfig.DisplayMode.PULSE) {
                    long elapsed = System.currentTimeMillis() - pulseStartTime.getOrDefault(id, System.currentTimeMillis());
                    long cyclePos = elapsed % 8000; // 8 second cycle
                    if (cyclePos > 3000) {
                        // In the hidden phase (3000-8000ms)
                        return;
                    }
                }

                // Build the combat HUD text
                CombatTagManager.CombatTag tag = combatTagManager.getCombatTag(p);
                if (tag == null) return;

                long remaining = tag.getRemainingMs(System.currentTimeMillis());
                String timeStr = tag.getFormattedRemaining(remaining);
                String hudText = buildHudText(tag, timeStr, p, mode);

                p.sendMessage(Text.literal(hudText), true);
            });
        }, 0, 200, TimeUnit.MILLISECONDS); // Update 5x per second

        activeHuds.put(id, future);
    }

    private String buildHudText(CombatTagManager.CombatTag tag, String timeStr,
                                ServerPlayerEntity player, CombatDisplayConfig.DisplayMode mode) {
        StringBuilder sb = new StringBuilder();

        // Color based on combat type
        String color = switch (tag.causeType) {
            case "PLAYER" -> "§c"; // red for PvP
            case "MOB" -> "§6";    // gold for mobs
            default -> "§e";       // yellow for environment
        };

        // Main line: ☠ IN DANGER — <cause> <time>
        sb.append(color).append("§lIN DANGER§r ");
        sb.append("§7— ");

        // Context: who/what caused it
        if ("PLAYER".equals(tag.causeType)) {
            sb.append("§f").append(tag.causeName).append(" ");
        } else if ("MOB".equals(tag.causeType)) {
            sb.append("§f").append(tag.causeName).append(" ");
        } else {
            sb.append("§f").append(tag.causeName).append(" ");
        }

        sb.append(color).append(timeStr);

        // Warning text (only in ALWAYS mode)
        if (mode == CombatDisplayConfig.DisplayMode.ALWAYS) {
            float healthPercent = player.getHealth() / player.getMaxHealth();
            if ("PLAYER".equals(tag.causeType)) {
                sb.append(" §8| §7Logging out = death");
            } else if (healthPercent < 0.5f) {
                sb.append(" §8| §7Low HP — logging out = death");
            }
        }

        return sb.toString();
    }

    /**
     * Check if the ThinkingSpinner is currently showing for this player.
     * We check if the spinner is enabled AND if there's an active spinner state.
     */
    private boolean isSpinnerActive(UUID playerId) {
        // ThinkingSpinner tracks active spinners internally — we check if it's enabled
        // and if there's a spinner running. Since we can't access the internal map directly,
        // we use the isEnabled check + a simple heuristic: if the spinner was started
        // for this player, it will be in the activeSpinners map.
        // For now, we'll add a public method to ThinkingSpinner to check this.
        return thinkingSpinner.isActive(playerId);
    }

    private void stopHud(UUID playerId) {
        ScheduledFuture<?> future = activeHuds.remove(playerId);
        if (future != null) {
            future.cancel(false);
        }
        pulseStartTime.remove(playerId);
    }

    /** Called when a player disconnects — clean up state. */
    public void onPlayerDisconnect(UUID playerId) {
        knownInCombat.remove(playerId);
        stopHud(playerId);
    }

    public void shutdown() {
        activeHuds.values().forEach(f -> f.cancel(false));
        activeHuds.clear();
        knownInCombat.clear();
        scheduler.shutdown();
    }
}

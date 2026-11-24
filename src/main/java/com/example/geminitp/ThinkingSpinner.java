package com.example.geminitp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * Animated action bar spinner shown while waiting for AI responses.
 * Uses braille dot characters for a smooth spinning animation.
 * Automatically fades when stopped since action bar text is ephemeral.
 */
public class ThinkingSpinner {

    private static final String[] FRAMES = {
        "§b● §7○ ○ §7",
        "§7○ §b● §7○ §7",
        "§7○ ○ §b● §7",
        "§7○ §b● §7○ §7"
    };

    private static final String[] MESSAGES = {
        "Bernard is thinking...",
        "Bernard is pondering...",
        "Bernard is cooking...",
        "Bernard is consulting the stars...",
        "Bernard is crunching numbers...",
        "Bernard is drafting a response...",
        "Bernard is in a meeting...",
        "Bernard is checking his notes...",
        "Bernard is on it...",
        "Bernard is doing research...",
        "Bernard is brainstorming...",
        "Bernard is loading wisdom...",
        "Bernard is processing...",
        "Bernard is reviewing the data...",
        "Bernard is almost ready..."
    };

    private static final java.util.Random RANDOM = new java.util.Random();

    private final MinecraftServer server;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, SpinnerState> activeSpinners = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    public ThinkingSpinner(MinecraftServer server) {
        this.server = server;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bernard-spinner");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (disabledPlayers.contains(id)) return;
        // Stop existing spinner if any
        stop(id);

        SpinnerState state = new SpinnerState();
        state.future = scheduler.scheduleAtFixedRate(() -> {
            server.execute(() -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p == null) {
                    stop(id);
                    return;
                }
                int frame = state.frame % FRAMES.length;
                // Switch message randomly every ~20 frames (~3 seconds)
                if (state.frame % 20 == 0) {
                    state.currentMessage = MESSAGES[RANDOM.nextInt(MESSAGES.length)];
                }
                String text = FRAMES[frame] + state.currentMessage;
                p.sendMessage(Text.literal(text), true); // true = action bar
                state.frame++;
            });
        }, 0, 150, TimeUnit.MILLISECONDS);

        activeSpinners.put(id, state);
    }

    public void stop(ServerPlayerEntity player) {
        stop(player.getUuid());
    }

    public void stop(UUID playerId) {
        SpinnerState state = activeSpinners.remove(playerId);
        if (state != null && state.future != null) {
            state.future.cancel(false);
            // Send one final empty action bar to clear it faster
            server.execute(() -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
                if (p != null) {
                    p.sendMessage(Text.literal(""), true);
                }
            });
        }
    }

    /** Toggle the thinking indicator for a player. Returns true if now enabled. */
    public boolean toggle(UUID playerId) {
        if (disabledPlayers.contains(playerId)) {
            disabledPlayers.remove(playerId);
            return true;
        } else {
            disabledPlayers.add(playerId);
            stop(playerId); // Stop any active spinner
            return false;
        }
    }

    public boolean isEnabled(UUID playerId) {
        return !disabledPlayers.contains(playerId);
    }

    /** Check if a spinner is currently active (running) for this player. */
    public boolean isActive(UUID playerId) {
        return activeSpinners.containsKey(playerId);
    }

    public void shutdown() {
        activeSpinners.values().forEach(s -> {
            if (s.future != null) s.future.cancel(false);
        });
        activeSpinners.clear();
        scheduler.shutdown();
    }

    private static class SpinnerState {
        ScheduledFuture<?> future;
        int frame = 0;
        String currentMessage = MESSAGES[RANDOM.nextInt(MESSAGES.length)];
    }
}

package com.gillodaby.betterhud;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class BetterHudService {

    private final Map<UUID, TrackedHud> huds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refresher;
    private static final long REFRESH_INTERVAL_MS = 500;

    BetterHudService() {
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "BetterHUD-Refresher");
            t.setDaemon(true);
            return t;
        };
        this.refresher = Executors.newSingleThreadScheduledExecutor(factory);
    }

    void start() {
        refresher.scheduleAtFixedRate(this::refreshAll, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    void handlePlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        PlayerRef playerRef = player.getPlayerRef();
        UUID id = playerRef.getUuid();

        var inventory = player.getInventory();
        ItemContainer armor = inventory.getArmor();
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        ItemContainer backpack = inventory.getBackpack();
        ItemContainer utility = inventory.getUtility();
        ItemContainer tools = inventory.getTools();
        TrackedHud existing = huds.get(id);
        if (existing != null) {
            refreshHud(existing);
            return;
        }

        // Defer opening the HUD to let the client finish ClientReady and asset downloads
        refresher.schedule(() -> {
            try {
                BetterHudHud hud = new BetterHudHud(playerRef);
                refreshHud(hud, player, armor);
                MultipleHUD.getInstance().setCustomHud(player, playerRef, "BetterHUD", hud);
                ensureThreadSafeMultipleHud(player);
                List<EventRegistration> listeners = new ArrayList<>();
                listeners.add(registerListener(armor, hud, player, armor));
                listeners.add(registerListener(hotbar, hud, player, armor));
                listeners.add(registerListener(storage, hud, player, armor));
                listeners.add(registerListener(backpack, hud, player, armor));
                listeners.add(registerListener(utility, hud, player, armor));
                listeners.add(registerListener(tools, hud, player, armor));
                huds.put(id, new TrackedHud(hud, listeners, armor, player));
                System.out.println("[BetterHUD] HUD overlay shown for " + player.getDisplayName());
            } catch (Throwable t) {
                System.out.println("[BetterHUD] Failed to show HUD for " + player.getDisplayName() + ": " + t.getMessage());
            }
        }, 2, TimeUnit.SECONDS);
    }

    void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) return;
        UUID id = playerRef.getUuid();
        TrackedHud tracked = huds.remove(id);
        if (tracked != null) {
            tracked.close();
        }
    }

    private void refreshAll() {
        for (TrackedHud tracked : huds.values()) {
            try {
                refreshHud(tracked);
            } catch (Throwable ignored) {
            }
        }
    }

    void refreshPlayer(Player player) {
        if (player == null) return;
        TrackedHud tracked = huds.get(player.getPlayerRef().getUuid());
        if (tracked != null) {
            refreshHud(tracked);
        }
    }

    private void refreshHud(TrackedHud tracked) {
        refreshHud(tracked.hud, tracked.player, tracked.armor);
    }

    private void refreshHud(BetterHudHud hud, Player player, ItemContainer armor) {
        ItemContainer allItems = player.getInventory().getCombinedEverything();
        hud.refresh(player, armor, allItems);
    }

    private EventRegistration registerListener(ItemContainer container, BetterHudHud hud, Player player, ItemContainer armor) {
        if (container == null) {
            return null;
        }
        return container.registerChangeEvent(ev -> refreshHud(hud, player, armor));
    }

    /**
     * MultipleHUD stores its HUDs inside a plain HashMap; swapping it to a ConcurrentHashMap
     * avoids concurrent modification when other plugins rebuild HUDs on tick threads.
     */
    private void ensureThreadSafeMultipleHud(Player player) {
        if (player == null) {
            return;
        }
        try {
            var hudManager = player.getHudManager();
            if (hudManager == null) return;
            CustomUIHud current = hudManager.getCustomHud();

            Class<?> multiClass = Class.forName("com.buuz135.mhud.MultipleCustomUIHud");
            if (!multiClass.isInstance(current)) {
                return;
            }

            var field = multiClass.getDeclaredField("customHuds");
            field.setAccessible(true);
            Object existing = field.get(current);
            if (existing instanceof java.util.concurrent.ConcurrentHashMap) {
                return;
            }
            Map<String, CustomUIHud> safe = new java.util.concurrent.ConcurrentHashMap<>();
            if (existing instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key && entry.getValue() instanceof CustomUIHud value) {
                        safe.put(key, value);
                    }
                }
            }
            field.set(current, safe);
        } catch (Throwable ignored) {
            // If reflection fails, fall back to default behavior.
        }
    }

    private static final class TrackedHud {
        final BetterHudHud hud;
        final List<EventRegistration> listeners;
        final ItemContainer armor;
        final Player player;

        TrackedHud(BetterHudHud hud, List<EventRegistration> listeners, ItemContainer armor, Player player) {
            this.hud = hud;
            this.listeners = listeners;
            this.armor = armor;
            this.player = player;
        }

        void close() {
            if (listeners == null) {
                return;
            }
            for (EventRegistration listener : listeners) {
                if (listener != null) {
                    listener.unregister();
                }
            }
            if (player != null) {
                MultipleHUD.getInstance().hideCustomHud(player, player.getPlayerRef(), "BetterHUD");
            }
        }
    }
}

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
    private final Map<UUID, TrackedHud> hiddenHuds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refresher;
    private static final long REFRESH_INTERVAL_MS = 1000;
    private static final long ARMOR_REFRESH_MS = 5000;
    private static final long ARROWS_REFRESH_MS = 2000;
    private static final long MAIN_REFRESH_MS = 1000;

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

        TrackedHud hidden = hiddenHuds.remove(id);
        if (hidden != null) {
            hidden.close();
        }

        var inventory = player.getInventory();
        ItemContainer armor = inventory.getArmor();
        ItemContainer hotbar = inventory.getHotbar();
        ItemContainer storage = inventory.getStorage();
        ItemContainer backpack = inventory.getBackpack();
        ItemContainer utility = inventory.getUtility();
        ItemContainer tools = inventory.getTools();
        TrackedHud existing = huds.get(id);
        if (existing != null) {
            refreshHud(existing, System.currentTimeMillis());
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
                TrackedHud tracked = new TrackedHud(hud, listeners, armor, player);
                long now = System.currentTimeMillis();
                tracked.lastArmorRefresh = now;
                tracked.lastArrowsRefresh = now;
                tracked.lastMainRefresh = now;
                listeners.add(registerListener(armor, tracked, RefreshKind.ARMOR));
                listeners.add(registerListener(hotbar, tracked, RefreshKind.MAIN_AND_ARROWS));
                listeners.add(registerListener(storage, tracked, RefreshKind.ARROWS));
                listeners.add(registerListener(backpack, tracked, RefreshKind.ARROWS));
                listeners.add(registerListener(utility, tracked, RefreshKind.ARROWS));
                listeners.add(registerListener(tools, tracked, RefreshKind.ARROWS));
                huds.put(id, tracked);
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
        TrackedHud hidden = hiddenHuds.remove(id);
        if (hidden != null) {
            hidden.close();
        }
    }

    private void refreshAll() {
        long now = System.currentTimeMillis();
        for (TrackedHud tracked : huds.values()) {
            try {
                refreshHud(tracked, now);
            } catch (Throwable ignored) {
            }
        }
    }

    void refreshPlayer(Player player) {
        if (player == null) return;
        TrackedHud tracked = huds.get(player.getPlayerRef().getUuid());
        if (tracked != null) {
            long now = System.currentTimeMillis();
            tracked.lastArmorRefresh = 0L;
            tracked.lastArrowsRefresh = 0L;
            tracked.lastMainRefresh = 0L;
            refreshHud(tracked, now);
        }
    }

    void hideHud(Player player) {
        if (player == null) return;
        UUID id = player.getPlayerRef().getUuid();
        TrackedHud tracked = huds.remove(id);
        if (tracked != null) {
            tracked.visible = false;
            hiddenHuds.put(id, tracked);
            MultipleHUD.getInstance().hideCustomHud(player, player.getPlayerRef(), "BetterHUD");
            System.out.println("[BetterHUD] HUD hidden for " + player.getDisplayName());
        }
    }

    void showHud(Player player) {
        if (player == null) return;
        UUID id = player.getPlayerRef().getUuid();
        TrackedHud tracked = hiddenHuds.remove(id);
        if (tracked != null) {
            tracked.visible = true;
            huds.put(id, tracked);
            MultipleHUD.getInstance().setCustomHud(player, player.getPlayerRef(), "BetterHUD", tracked.hud);
            ensureThreadSafeMultipleHud(player);
            System.out.println("[BetterHUD] HUD shown for " + player.getDisplayName());
        }
    }

    private void refreshHud(TrackedHud tracked, long now) {
        if (tracked == null || !tracked.visible) {
            return;
        }
        Player player = tracked.player;
        if (player == null || player.wasRemoved()) {
            return;
        }

        if (now - tracked.lastArmorRefresh >= ARMOR_REFRESH_MS) {
            refreshArmor(tracked);
            tracked.lastArmorRefresh = now;
        }

        if (now - tracked.lastArrowsRefresh >= ARROWS_REFRESH_MS) {
            refreshArrows(tracked);
            tracked.lastArrowsRefresh = now;
        }

        if (now - tracked.lastMainRefresh >= MAIN_REFRESH_MS) {
            refreshMainHand(tracked);
            tracked.lastMainRefresh = now;
        }
    }

    private void refreshArmor(TrackedHud tracked) {
        if (tracked == null) return;
        Player player = tracked.player;
        if (player == null || player.wasRemoved()) return;
        tracked.hud.refreshArmor(player, tracked.armor);
    }

    private void refreshArrows(TrackedHud tracked) {
        if (tracked == null) return;
        Player player = tracked.player;
        if (player == null || player.wasRemoved()) return;
        ItemContainer allItems = player.getInventory().getCombinedEverything();
        tracked.hud.refreshArrows(player, allItems);
    }

    private void refreshMainHand(TrackedHud tracked) {
        if (tracked == null) return;
        Player player = tracked.player;
        if (player == null || player.wasRemoved()) return;
        tracked.hud.refreshMainHand(player);
    }

    private void refreshHud(BetterHudHud hud, Player player, ItemContainer armor) {
        if (hud == null || player == null || player.wasRemoved()) {
            return;
        }
        ItemContainer allItems = player.getInventory().getCombinedEverything();
        hud.refresh(player, armor, allItems);
    }

    private EventRegistration registerListener(ItemContainer container, TrackedHud tracked, RefreshKind kind) {
        if (container == null) {
            return null;
        }
        return container.registerChangeEvent(ev -> refreshByKind(tracked, kind));
    }

    private void refreshByKind(TrackedHud tracked, RefreshKind kind) {
        if (tracked == null || !tracked.visible) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (kind) {
            case ARMOR -> {
                refreshArmor(tracked);
                tracked.lastArmorRefresh = now;
            }
            case ARROWS -> {
                refreshArrows(tracked);
                tracked.lastArrowsRefresh = now;
            }
            case MAIN_AND_ARROWS -> {
                refreshMainHand(tracked);
                refreshArrows(tracked);
                tracked.lastMainRefresh = now;
                tracked.lastArrowsRefresh = now;
            }
        }
    }

    private enum RefreshKind {
        ARMOR,
        ARROWS,
        MAIN_AND_ARROWS
    }

    /**
     * MultipleHUD stores its HUDs inside a plain HashMap; swapping it to a ConcurrentHashMap
     * avoids concurrent modification when other plugins rebuild HUDs on tick threads.
     */
    private static void ensureThreadSafeMultipleHud(Player player) {
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
        volatile boolean visible = true;
        volatile long lastArmorRefresh = 0L;
        volatile long lastArrowsRefresh = 0L;
        volatile long lastMainRefresh = 0L;

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
                ensureThreadSafeMultipleHud(player);
                MultipleHUD.getInstance().hideCustomHud(player, player.getPlayerRef(), "BetterHUD");
            }
        }
    }
}

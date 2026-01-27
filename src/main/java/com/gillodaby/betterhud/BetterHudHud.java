package com.gillodaby.betterhud;

import com.hypixel.hytale.function.consumer.ShortObjectConsumer;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Lightweight HUD overlay that does not block player interactions.
 */
final class BetterHudHud extends CustomUIHud {

    private static final DecimalFormat PERCENT = new DecimalFormat("0");
    private static final DecimalFormat COUNT = new DecimalFormat("###,###");
    private static final String[] SLOT_IDS = {"Head", "Chest", "Legs", "Feet"};
    private static final String ARROW_TOKEN = "weapon_arrow";
    private Player cachedPlayer;
    private ItemContainer cachedArmor;
    private ItemContainer cachedAllItems;

    BetterHudHud(PlayerRef ref) {
        super(ref);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        writeHud(builder, cachedArmor, cachedPlayer, cachedAllItems);
    }

    void refresh(Player player, ItemContainer armor, ItemContainer allItems) {
        this.cachedPlayer = player;
        this.cachedArmor = armor;
        this.cachedAllItems = allItems;
        UICommandBuilder builder = new UICommandBuilder();
        writeHud(builder, armor, player, allItems);
        // partial update is enough now that layout is static
        update(false, builder);
    }

    void refreshArmor(Player player, ItemContainer armor) {
        this.cachedPlayer = player;
        this.cachedArmor = armor;
        UICommandBuilder builder = new UICommandBuilder();
        builder.append("Pages/GilloDaby_BetterHUD.ui");
        writeArmorSection(builder, armor, player);
        update(false, builder);
    }

    void refreshArrows(Player player, ItemContainer allItems) {
        this.cachedPlayer = player;
        this.cachedAllItems = allItems;
        UICommandBuilder builder = new UICommandBuilder();
        builder.append("Pages/GilloDaby_BetterHUD.ui");
        writeArrowsSection(builder, allItems);
        update(false, builder);
    }

    void refreshMainHand(Player player) {
        this.cachedPlayer = player;
        UICommandBuilder builder = new UICommandBuilder();
        builder.append("Pages/GilloDaby_BetterHUD.ui");
        writeMainHandSection(builder, player);
        update(false, builder);
    }

    private void writeHud(UICommandBuilder builder, ItemContainer armor, Player player, ItemContainer allItems) {
        builder.append("Pages/GilloDaby_BetterHUD.ui");
        writeArmorSection(builder, armor, player);
        writeArrowsSection(builder, allItems);
        writeMainHandSection(builder, player);
    }

    private void writeArmorSection(UICommandBuilder builder, ItemContainer armor, Player player) {
        if (armor == null || player == null) {
            clearArmor(builder);
            return;
        }

        int capacity = Math.min(armor.getCapacity(), SLOT_IDS.length);
        for (int i = 0; i < SLOT_IDS.length; i++) {
            String id = SLOT_IDS[i];
            String valueSelector = "#" + id + "Value.Text";
            String iconSelector = "#" + id + "Icon.ItemId";

            if (i >= capacity) {
                builder.set(valueSelector, "");
                builder.setNull(iconSelector);
                continue;
            }

            ItemStack stack = armor.getItemStack((short) i);
            if (stack == null || stack.isEmpty()) {
                builder.set(valueSelector, "");
                builder.setNull(iconSelector);
                continue;
            }

            double max = stack.getMaxDurability();
            double current = stack.getDurability();
            double pct = (max <= 0) ? 100.0 : Math.max(0, Math.min(100, (current / max) * 100.0));
            String text = (max <= 0) ? "INF" : PERCENT.format(pct) + "%";
            // add a space before value so name and value don't touch
            text = " " + text;

            builder.set(valueSelector, text);
            builder.set(iconSelector, stack.getItemId());
        }
    }

    private void clearArmor(UICommandBuilder builder) {
        for (String id : SLOT_IDS) {
            builder.set("#" + id + "Value.Text", "");
            builder.setNull("#" + id + "Icon.ItemId");
        }
    }

    private void writeArrowsSection(UICommandBuilder builder, ItemContainer allItems) {
        String valueSelector = "#ArrowsValue.Text";
        String iconSelector = "#ArrowsIcon.ItemId";

        if (allItems == null) {
            hideArrows(builder);
            return;
        }

        ArrowSummary summary = countArrows(allItems);
        boolean hasArrows = summary.total > 0;
        builder.set("#Arrows.Visible", hasArrows);
        builder.set(valueSelector, hasArrows ? COUNT.format(summary.total) : "0");
        if (!hasArrows || summary.iconItemId == null) {
            builder.setNull(iconSelector);
        } else {
            builder.set(iconSelector, summary.iconItemId);
        }
    }

    private void hideArrows(UICommandBuilder builder) {
        builder.set("#Arrows.Visible", false);
        builder.set("#ArrowsValue.Text", "0");
        builder.setNull("#ArrowsIcon.ItemId");
    }

    private void writeMainHandSection(UICommandBuilder builder, Player player) {
        String visibleSelector = "#MainHand.Visible";
        String valueSelector = "#MainValue.Text";
        String iconSelector = "#MainIcon.ItemId";

        if (player == null) {
            hideMainHand(builder);
            return;
        }

        ItemStack stack = player.getInventory().getItemInHand();
        if (stack == null || stack.isEmpty()) {
            hideMainHand(builder);
            return;
        }

        Item item = stack.getItem();
        boolean isTool = item != null && (item.getTool() != null || item.getWeapon() != null);
        if (!isTool) {
            hideMainHand(builder);
            return;
        }

        double max = stack.getMaxDurability();
        double current = stack.getDurability();
        double pct = (max <= 0) ? 100.0 : Math.max(0, Math.min(100, (current / max) * 100.0));
        String text = (max <= 0) ? "INF" : PERCENT.format(pct) + "%";

        builder.set(visibleSelector, true);
        builder.set(valueSelector, text);
        builder.set(iconSelector, stack.getItemId());
    }

    private void hideMainHand(UICommandBuilder builder) {
        builder.set("#MainHand.Visible", false);
        builder.set("#MainValue.Text", "");
        builder.setNull("#MainIcon.ItemId");
    }

    private ArrowSummary countArrows(ItemContainer allItems) {
        ArrowSummary summary = new ArrowSummary();
        ShortObjectConsumer<ItemStack> counter = (short slot, ItemStack stack) -> {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            String itemId = stack.getItemId();
            String lower = itemId == null ? null : itemId.toLowerCase(Locale.ROOT);
            if (lower != null && lower.contains(ARROW_TOKEN)) {
                summary.total += stack.getQuantity();
                if (summary.iconItemId == null) {
                    summary.iconItemId = itemId;
                }
            }
        };

        allItems.forEach(counter);
        return summary;
    }

    private static final class ArrowSummary {
        int total = 0;
        String iconItemId = null;
    }
}

package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.compat.create.DisplayBoardWriter;
import com.gr4v1ty.supplylines.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the display board showing incoming shipments. Coordinates content
 * from multiple sources (RestockManager, SpeculativeOrderManager).
 */
public final class DisplayBoardManager {

    private long lastDisplayUpdateTick = Long.MIN_VALUE;

    /** Gets the display board update interval from config */
    private static int getDisplayUpdateIntervalTicks() {
        return ModConfig.SERVER.displayUpdateIntervalTicks.get();
    }

    /** Gets the item name truncation length from config */
    private static int getItemNameTruncation() {
        return ModConfig.SERVER.itemNameTruncation.get();
    }

    /** Gets the ETA format threshold in seconds from config */
    private static int getEtaFormatThresholdSeconds() {
        return ModConfig.SERVER.etaFormatThresholdSeconds.get();
    }

    /**
     * Updates the display board if the update interval has elapsed.
     *
     * @param level
     *            The world
     * @param displayBoardPos
     *            Position of the display board (may be null)
     * @param now
     *            Current game tick
     * @param orders
     *            All incoming orders to display
     */
    public void updateDisplayIfDue(Level level, @Nullable BlockPos displayBoardPos, long now,
            Collection<? extends IncomingOrder> orders) {
        if (displayBoardPos == null) {
            return;
        }

        if (lastDisplayUpdateTick != Long.MIN_VALUE && now - lastDisplayUpdateTick < getDisplayUpdateIntervalTicks()) {
            return;
        }
        lastDisplayUpdateTick = now;

        updateDisplay(level, displayBoardPos, now, orders);
    }

    /**
     * Formats and writes incoming orders to the display board.
     */
    private void updateDisplay(Level level, BlockPos displayBoardPos, long now,
            Collection<? extends IncomingOrder> orders) {

        if (orders.isEmpty()) {
            DisplayBoardWriter.clearDisplay(level, displayBoardPos);
            return;
        }

        // Sort by ETA (earliest first)
        List<IncomingOrder> sortedOrders = new ArrayList<>(orders);
        sortedOrders.sort(Comparator.comparingLong(IncomingOrder::getEstimatedArrivalTick));

        List<Component> lines = new ArrayList<>();

        // Header line
        lines.add(Component.literal("Incoming Shipments").withStyle(ChatFormatting.GOLD));

        int maxNameLen = getItemNameTruncation();
        for (IncomingOrder order : sortedOrders) {
            String itemName = truncateString(order.getItem().getDisplayName().getString(), maxNameLen);
            int qty = order.getQuantity();
            String eta = formatETA(order.getEstimatedArrivalTick() - now);

            // Format: "ItemName x64 ~30s"
            Component line = Component.literal(String.format("%-" + maxNameLen + "s x%-4d %s", itemName, qty, eta));
            lines.add(line);
        }

        DisplayBoardWriter.writeLines(level, displayBoardPos, lines);
    }

    /**
     * Formats remaining ticks as a human-readable ETA string.
     */
    private String formatETA(long ticksRemaining) {
        if (ticksRemaining <= 0) {
            return "arriving";
        }
        int seconds = (int) (ticksRemaining / 20);
        if (seconds < getEtaFormatThresholdSeconds()) {
            return "~" + seconds + "s";
        }
        int minutes = seconds / 60;
        return "~" + minutes + "m";
    }

    /**
     * Truncates a string to the specified maximum length.
     */
    private String truncateString(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 1) + ".";
    }
}

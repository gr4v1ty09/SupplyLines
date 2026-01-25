package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.manager.SpeculativeOrderManager.SpeculativeOrder;
import com.gr4v1ty.supplylines.compat.create.DisplayBoardWriter;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Manages the display board showing incoming shipments. Owns order tracking
 * state and receives events from RestockManager and SpeculativeOrderManager.
 */
public final class DisplayBoardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayBoardManager.class);

    private long lastDisplayUpdateTick = Long.MIN_VALUE;

    /**
     * Active orders keyed by item. Supports multiple orders per item type. Uses
     * LinkedHashMap to maintain insertion order for FIFO processing.
     */
    private final Map<ItemMatch.ItemStackKey, List<IncomingOrder>> activeOrders = new LinkedHashMap<>();

    /**
     * Index of speculative orders by request ID for fast lookup during cleanup.
     */
    private final Map<IToken<?>, SpeculativeOrder> speculativeOrdersByRequest = new LinkedHashMap<>();

    /** Listener for order cleared events (item key when order completes) */
    @Nullable
    private Consumer<ItemMatch.ItemStackKey> orderClearedListener;

    /** Provider for order expiry buffer ticks (per-building setting) */
    @Nullable
    private IntSupplier orderExpiryBufferProvider;

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

    /** Gets the extra buffer time after ETA before removing order */
    private long getOrderExpiryBufferTicks() {
        return orderExpiryBufferProvider != null
                ? orderExpiryBufferProvider.getAsInt()
                : ModConfig.SERVER.orderExpiryBufferTicks.get();
    }

    /**
     * Sets the provider for order expiry buffer ticks.
     *
     * @param provider
     *            Supplier for the expiry buffer value, or null for global config
     */
    public void setOrderExpiryBufferProvider(@Nullable IntSupplier provider) {
        this.orderExpiryBufferProvider = provider;
    }

    /**
     * Sets the listener for order cleared events.
     *
     * @param listener
     *            Consumer to be notified when orders are cleared
     */
    public void setOrderClearedListener(@Nullable Consumer<ItemMatch.ItemStackKey> listener) {
        this.orderClearedListener = listener;
    }

    // ==================== Event Handlers ====================

    /**
     * Called when an order is placed (by RestockManager or
     * SpeculativeOrderManager). Adds the order to the tracking map for display.
     *
     * @param order
     *            The order that was placed
     */
    public void onOrderPlaced(IncomingOrder order) {
        ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(order.getItem());
        List<IncomingOrder> orderList = activeOrders.computeIfAbsent(itemKey, k -> new ArrayList<>());
        orderList.add(order);

        // Index speculative orders by request ID for fast cleanup
        if (order instanceof SpeculativeOrder specOrder) {
            speculativeOrdersByRequest.put(specOrder.forRequestId, specOrder);
        }

        LOGGER.debug("Order tracked: {} x{}", order.getItem().getDisplayName().getString(), order.getQuantity());
    }

    /**
     * Called when stock arrives in the local network (detected via stock delta).
     * Attempts to match arrivals to active orders using exact match then FIFO.
     *
     * @param itemKey
     *            The item that arrived
     * @param quantityArrived
     *            The quantity increase detected
     */
    public void onStockArrived(ItemMatch.ItemStackKey itemKey, long quantityArrived) {
        List<IncomingOrder> orders = activeOrders.get(itemKey);
        if (orders == null || orders.isEmpty()) {
            return;
        }

        // Pass 1: Try exact quantity match
        for (Iterator<IncomingOrder> it = orders.iterator(); it.hasNext();) {
            IncomingOrder order = it.next();
            if (order.getQuantity() == quantityArrived) {
                it.remove();
                removeFromSpeculativeIndex(order);
                if (orders.isEmpty()) {
                    activeOrders.remove(itemKey);
                }
                LOGGER.debug("Order cleared (exact match): {} x{}", order.getItem().getDisplayName().getString(),
                        order.getQuantity());
                notifyOrderCleared(itemKey);
                return;
            }
        }

        // Pass 2: FIFO fallback - clear oldest order if quantity >= order amount
        IncomingOrder oldest = orders.get(0);
        if (quantityArrived >= oldest.getQuantity()) {
            orders.remove(0);
            removeFromSpeculativeIndex(oldest);
            if (orders.isEmpty()) {
                activeOrders.remove(itemKey);
            }
            LOGGER.debug("Order cleared (FIFO): {} x{}", oldest.getItem().getDisplayName().getString(),
                    oldest.getQuantity());
            notifyOrderCleared(itemKey);
        }
    }

    /**
     * Called when a colony request completes (fallback cleanup for speculative
     * orders).
     *
     * @param requestId
     *            The request ID that completed
     */
    public void onRequestCompleted(IToken<?> requestId) {
        SpeculativeOrder order = speculativeOrdersByRequest.remove(requestId);
        if (order == null) {
            return;
        }

        // Remove from main tracking map
        ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(order.getItem());
        List<IncomingOrder> orders = activeOrders.get(itemKey);
        if (orders != null) {
            orders.remove(order);
            if (orders.isEmpty()) {
                activeOrders.remove(itemKey);
            }
        }

        LOGGER.debug("Speculative order cleared (request completed): {} x{}",
                order.getItem().getDisplayName().getString(), order.getQuantity());
        notifyOrderCleared(itemKey);
    }

    /**
     * Removes orders that are past ETA + buffer time (fallback for failed
     * deliveries).
     *
     * @param now
     *            Current game tick
     */
    public void cleanupExpiredOrders(long now) {
        Iterator<Map.Entry<ItemMatch.ItemStackKey, List<IncomingOrder>>> it = activeOrders.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<ItemMatch.ItemStackKey, List<IncomingOrder>> entry = it.next();
            ItemMatch.ItemStackKey itemKey = entry.getKey();
            List<IncomingOrder> orders = entry.getValue();
            int sizeBefore = orders.size();

            orders.removeIf(order -> {
                long expiryTick = order.getEstimatedArrivalTick() + getOrderExpiryBufferTicks();
                if (now > expiryTick) {
                    removeFromSpeculativeIndex(order);
                    LOGGER.warn("Expiring order for {} (past ETA)", order.getItem().getDisplayName().getString());
                    return true;
                }
                return false;
            });

            if (orders.size() < sizeBefore) {
                notifyOrderCleared(itemKey);
            }

            if (orders.isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * Helper to remove an order from the speculative index if applicable.
     */
    private void removeFromSpeculativeIndex(IncomingOrder order) {
        if (order instanceof SpeculativeOrder specOrder) {
            speculativeOrdersByRequest.remove(specOrder.forRequestId);
        }
    }

    /**
     * Notifies the order cleared listener if one is set.
     */
    private void notifyOrderCleared(ItemMatch.ItemStackKey itemKey) {
        if (orderClearedListener != null) {
            orderClearedListener.accept(itemKey);
        }
    }

    // ==================== Query Methods ====================

    /**
     * Gets all active orders for display purposes.
     *
     * @return Collection of all active orders
     */
    public Collection<IncomingOrder> getActiveOrders() {
        List<IncomingOrder> allOrders = new ArrayList<>();
        for (List<IncomingOrder> orderList : activeOrders.values()) {
            allOrders.addAll(orderList);
        }
        return allOrders;
    }

    /**
     * Gets the count of currently active orders.
     */
    public int getActiveOrderCount() {
        return activeOrders.values().stream().mapToInt(List::size).sum();
    }

    // ==================== Display Methods ====================

    /**
     * Updates the display board if the update interval has elapsed. Uses internally
     * tracked orders.
     *
     * @param level
     *            The world
     * @param displayBoardPos
     *            Position of the display board (may be null)
     * @param now
     *            Current game tick
     */
    public void updateDisplayIfDue(Level level, @Nullable BlockPos displayBoardPos, long now) {
        if (displayBoardPos == null) {
            return;
        }

        if (lastDisplayUpdateTick != Long.MIN_VALUE && now - lastDisplayUpdateTick < getDisplayUpdateIntervalTicks()) {
            return;
        }
        lastDisplayUpdateTick = now;

        // Run expiry cleanup before display update
        cleanupExpiredOrders(now);

        updateDisplay(level, displayBoardPos, now);
    }

    /**
     * Formats and writes incoming orders to the display board.
     */
    private void updateDisplay(Level level, BlockPos displayBoardPos, long now) {
        Collection<IncomingOrder> orders = getActiveOrders();

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

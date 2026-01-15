package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule.PolicyEntry;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule.SupplierEntry;
import com.gr4v1ty.supplylines.compat.create.DisplayBoardWriter;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.gr4v1ty.supplylines.util.LogTags;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.MessageUtils;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages Level 5 automatic restocking from remote Create network suppliers.
 * Evaluates restock policies periodically and requests items from suppliers
 * when local stock falls below target levels.
 */
public final class RestockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestockManager.class);

    /** Display board update interval: 100 ticks = 5 seconds */
    private static final int DISPLAY_UPDATE_INTERVAL_TICKS = 100;

    /** Default assumed delivery time for ETA calculation */
    private static final long DEFAULT_DELIVERY_TICKS = 400;

    /** Extra buffer time after ETA before removing order from tracking */
    private static final long ORDER_EXPIRY_BUFFER_TICKS = 200;

    @SuppressWarnings("unused")
    private final IColony colony;
    private long lastRestockCheckTick = Long.MIN_VALUE;
    private long lastDisplayUpdateTick = Long.MIN_VALUE;

    /**
     * In-flight restock orders for display board. Transient - not persisted to NBT.
     */
    private final Map<ItemMatch.ItemStackKey, RestockOrder> activeOrders = new LinkedHashMap<>();

    /**
     * Represents an in-flight restock order for display purposes.
     */
    public static class RestockOrder {
        public final ItemStack item;
        public final int quantity;
        public final long requestedAtTick;
        public final UUID supplierNetworkId;

        public RestockOrder(ItemStack item, int quantity, long requestedAtTick, UUID supplierNetworkId) {
            this.item = item.copy();
            this.quantity = quantity;
            this.requestedAtTick = requestedAtTick;
            this.supplierNetworkId = supplierNetworkId;
        }

        public long getEstimatedArrivalTick() {
            return requestedAtTick + DEFAULT_DELIVERY_TICKS;
        }
    }

    /**
     * Result of finding a supplier with available stock.
     */
    private static class SupplierResult {
        final SupplierEntry supplier;
        final int availableQuantity;

        SupplierResult(SupplierEntry supplier, int availableQuantity) {
            this.supplier = supplier;
            this.availableQuantity = availableQuantity;
        }
    }

    /**
     * Represents a pending restock request ready to be batched with others destined
     * for the same supplier.
     */
    private static class PendingRestockRequest {
        final ItemStack item;
        final int quantity;
        final SupplierEntry supplier;

        PendingRestockRequest(ItemStack item, int quantity, SupplierEntry supplier) {
            this.item = item.copy();
            this.quantity = quantity;
            this.supplier = supplier;
        }
    }

    public RestockManager(IColony colony) {
        this.colony = colony;
    }

    /**
     * Main entry point called from BuildingStockKeeper.serverTick(). Evaluates
     * restock policies and requests items from suppliers when needed.
     *
     * @param level
     *            The world
     * @param policyModule
     *            Module containing restock policies
     * @param suppliersModule
     *            Module containing supplier networks
     * @param localNetwork
     *            Integration for querying local stock levels
     * @param displayBoardPos
     *            Position of display board (may be null)
     * @param restockIntervalTicks
     *            Interval between restock checks
     */
    public void processRestockPoliciesIfDue(Level level, RestockPolicyModule policyModule,
            SuppliersModule suppliersModule, NetworkIntegration localNetwork, @Nullable BlockPos displayBoardPos,
            int restockIntervalTicks) {

        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }

        // Update display board more frequently than restock checks
        if (shouldUpdateDisplay(now)) {
            updateDisplayBoard(level, displayBoardPos, now);
            lastDisplayUpdateTick = now;
        }

        // Check if restock evaluation is due
        if (lastRestockCheckTick != Long.MIN_VALUE && now - lastRestockCheckTick < restockIntervalTicks) {
            return;
        }
        lastRestockCheckTick = now;

        // Clean up expired/fulfilled orders
        cleanupExpiredOrders(now);

        // Get policies and suppliers
        List<PolicyEntry> policies = policyModule.getPolicies();
        List<SupplierEntry> suppliers = suppliersModule.getSuppliers();

        if (policies.isEmpty() || suppliers.isEmpty()) {
            return;
        }

        // Phase 1: Collect all pending restock requests
        List<PendingRestockRequest> pendingRequests = collectPendingRequests(policies, suppliers, localNetwork, now);

        if (pendingRequests.isEmpty()) {
            return;
        }

        // Phase 2: Group by supplier UUID
        Map<UUID, List<PendingRestockRequest>> bySupplier = pendingRequests.stream()
                .collect(Collectors.groupingBy(r -> r.supplier.getNetworkId()));

        // Phase 3: Send one batched request per supplier
        for (List<PendingRestockRequest> supplierRequests : bySupplier.values()) {
            sendBatchedRequest(level, supplierRequests, now);
        }
    }

    private boolean shouldUpdateDisplay(long now) {
        return lastDisplayUpdateTick == Long.MIN_VALUE || now - lastDisplayUpdateTick >= DISPLAY_UPDATE_INTERVAL_TICKS;
    }

    /**
     * Queries suppliers in priority order to find one with sufficient stock.
     *
     * @param item
     *            The item to find
     * @param requiredQuantity
     *            The quantity needed
     * @param suppliers
     *            List of suppliers sorted by priority
     * @return SupplierResult if found, null otherwise
     */
    @Nullable
    private SupplierResult findSupplierWithStock(ItemStack item, int requiredQuantity, List<SupplierEntry> suppliers) {

        ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(item);

        // Suppliers are already sorted by priority (0 = highest)
        for (SupplierEntry supplier : suppliers) {
            try {
                InventorySummary summary = LogisticsManager.getSummaryOfNetwork(supplier.getNetworkId(), false);

                if (summary == null || summary.isEmpty()) {
                    continue;
                }

                // Count matching items in supplier's network
                long available = 0;
                for (BigItemStack bigStack : summary.getStacks()) {
                    if (bigStack == null || bigStack.stack == null) {
                        continue;
                    }
                    ItemMatch.ItemStackKey stackKey = new ItemMatch.ItemStackKey(bigStack.stack);
                    if (stackKey.equals(itemKey)) {
                        available += bigStack.count;
                    }
                }

                if (available > 0) {
                    // Supplier has the item - check if it has a valid address
                    if (!supplier.hasValidAddress()) {
                        // Warn colony players about missing address
                        String label = supplier.getLabel().isEmpty()
                                ? supplier.getNetworkId().toString().substring(0, 8)
                                : supplier.getLabel();
                        MessageUtils
                                .format("com.supplylines.restock.noaddress", item.getDisplayName().getString(), label)
                                .sendTo(colony.getImportantMessageEntityPlayers());
                        LOGGER.debug("{} Supplier {} has {} but no delivery address configured", LogTags.ORDERING,
                                supplier.getNetworkId(), item.getDisplayName().getString());
                        continue;
                    }

                    int quantity = available >= requiredQuantity
                            ? requiredQuantity
                            : (int) Math.min(available, Integer.MAX_VALUE);
                    return new SupplierResult(supplier, quantity);
                }
            } catch (Exception e) {
                LOGGER.warn("{} Failed to query supplier network {}: {}", LogTags.ORDERING, supplier.getNetworkId(),
                        e.getMessage());
            }
        }

        return null;
    }

    /**
     * Evaluates all policies and collects pending restock requests without sending
     * them. This allows batching multiple items destined for the same supplier.
     *
     * @param policies
     *            List of restock policies to evaluate
     * @param suppliers
     *            List of available suppliers
     * @param localNetwork
     *            Integration for querying local stock levels
     * @param now
     *            Current game tick
     * @return List of pending requests ready to be batched by supplier
     */
    private List<PendingRestockRequest> collectPendingRequests(List<PolicyEntry> policies,
            List<SupplierEntry> suppliers, NetworkIntegration localNetwork, long now) {

        List<PendingRestockRequest> pendingRequests = new ArrayList<>();

        for (PolicyEntry policy : policies) {
            ItemStack policyItem = policy.getItem().getItemStack();
            int targetQuantity = policy.getTargetQuantity();

            // Get current local stock level
            long localStock = localNetwork.getStockLevel(policyItem);

            // Calculate deficit
            int deficit = targetQuantity - (int) Math.min(localStock, Integer.MAX_VALUE);
            if (deficit <= 0) {
                // Already at or above target, remove any active order for this item
                activeOrders.remove(new ItemMatch.ItemStackKey(policyItem));
                continue;
            }

            // Skip if we already have an active order for this item
            ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(policyItem);
            if (activeOrders.containsKey(itemKey)) {
                LOGGER.debug("{} Skipping restock for {} - order already in flight", LogTags.ORDERING,
                        policyItem.getDisplayName().getString());
                continue;
            }

            // Find supplier with sufficient stock
            SupplierResult result = findSupplierWithStock(policyItem, deficit, suppliers);
            if (result == null) {
                LOGGER.debug("{} No supplier found with {} x{}", LogTags.ORDERING,
                        policyItem.getDisplayName().getString(), deficit);
                continue;
            }

            // Add to pending list (don't send yet)
            pendingRequests.add(new PendingRestockRequest(policyItem, result.availableQuantity, result.supplier));
        }

        return pendingRequests;
    }

    /**
     * Sends a batched request for multiple items to a single supplier.
     *
     * @param level
     *            The world
     * @param requests
     *            List of pending requests (all for same supplier)
     * @param now
     *            Current game tick for tracking
     */
    private void sendBatchedRequest(Level level, List<PendingRestockRequest> requests, long now) {
        if (requests.isEmpty()) {
            return;
        }

        SupplierEntry supplier = requests.get(0).supplier;

        try {
            // Build combined item list
            List<BigItemStack> orderedStacks = new ArrayList<>();
            for (PendingRestockRequest req : requests) {
                orderedStacks.add(new BigItemStack(req.item.copy(), req.quantity));
            }

            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(orderedStacks);
            String destinationAddress = supplier.getRequestAddress();

            boolean success = LogisticsManager.broadcastPackageRequest(supplier.getNetworkId(),
                    LogisticallyLinkedBehaviour.RequestType.RESTOCK, order, null, destinationAddress);

            if (success) {
                // Track individual items for display board
                for (PendingRestockRequest req : requests) {
                    ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(req.item);
                    activeOrders.put(itemKey, new RestockOrder(req.item, req.quantity, now, supplier.getNetworkId()));
                }

                // Log batched request
                LOGGER.info("{} Batched {} item type(s) to supplier network {} (address: {}): {}", LogTags.ORDERING,
                        requests.size(), supplier.getNetworkId(), destinationAddress,
                        requests.stream().map(r -> r.item.getDisplayName().getString() + " x" + r.quantity)
                                .collect(Collectors.joining(", ")));
            } else {
                LOGGER.warn("{} Failed to broadcast batched restock request to network {} (address: {})",
                        LogTags.DISPATCH, supplier.getNetworkId(), destinationAddress);
            }
        } catch (Exception e) {
            LOGGER.error("{} Exception broadcasting batched restock request", LogTags.DISPATCH, e);
        }
    }

    /**
     * Sends a package request to a remote Create network.
     *
     * @param level
     *            The world
     * @param supplier
     *            The supplier entry containing network ID and request address
     * @param item
     *            The item to request
     * @param quantity
     *            The quantity to request
     * @return true if broadcast was successful
     */
    @SuppressWarnings("unused")
    private boolean requestFromSupplier(Level level, SupplierEntry supplier, ItemStack item, int quantity) {

        try {
            List<BigItemStack> orderedStacks = new ArrayList<>();
            orderedStacks.add(new BigItemStack(item.copy(), quantity));

            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(orderedStacks);

            // Use the supplier's configured request address
            String destinationAddress = supplier.getRequestAddress();

            boolean success = LogisticsManager.broadcastPackageRequest(supplier.getNetworkId(),
                    LogisticallyLinkedBehaviour.RequestType.RESTOCK, order, null, // No source filter
                    destinationAddress);

            if (!success) {
                LOGGER.warn("{} Failed to broadcast restock request to network {} (address: {})", LogTags.DISPATCH,
                        supplier.getNetworkId(), destinationAddress);
            }

            return success;
        } catch (Exception e) {
            LOGGER.error("{} Exception broadcasting restock request", LogTags.DISPATCH, e);
            return false;
        }
    }

    /**
     * Formats and writes active orders to the display board.
     */
    private void updateDisplayBoard(Level level, @Nullable BlockPos displayBoardPos, long now) {
        if (displayBoardPos == null) {
            return;
        }

        if (activeOrders.isEmpty()) {
            DisplayBoardWriter.clearDisplay(level, displayBoardPos);
            return;
        }

        // Build display lines sorted by ETA (earliest first)
        List<RestockOrder> sortedOrders = new ArrayList<>(activeOrders.values());
        sortedOrders.sort(Comparator.comparingLong(RestockOrder::getEstimatedArrivalTick));

        List<Component> lines = new ArrayList<>();

        // Header line
        lines.add(Component.literal("Incoming Shipments").withStyle(ChatFormatting.GOLD));

        for (RestockOrder order : sortedOrders) {
            String itemName = truncateString(order.item.getDisplayName().getString(), 12);
            int qty = order.quantity;
            String eta = formatETA(order.getEstimatedArrivalTick() - now);

            // Format: "ItemName x64 ~30s"
            Component line = Component.literal(String.format("%-12s x%-4d %s", itemName, qty, eta));
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
        if (seconds < 60) {
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

    /**
     * Removes orders that are past ETA + buffer time.
     */
    private void cleanupExpiredOrders(long now) {
        Iterator<Map.Entry<ItemMatch.ItemStackKey, RestockOrder>> it = activeOrders.entrySet().iterator();

        while (it.hasNext()) {
            RestockOrder order = it.next().getValue();
            long expiryTick = order.getEstimatedArrivalTick() + ORDER_EXPIRY_BUFFER_TICKS;
            if (now > expiryTick) {
                LOGGER.debug("{} Expiring restock order for {} (past ETA)", LogTags.ORDERING,
                        order.item.getDisplayName().getString());
                it.remove();
            }
        }
    }

    /**
     * Gets the count of currently active restock orders.
     */
    public int getActiveOrderCount() {
        return activeOrders.size();
    }
}

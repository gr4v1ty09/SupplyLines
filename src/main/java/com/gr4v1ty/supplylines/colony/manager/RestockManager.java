package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule.PolicyEntry;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule.SupplierEntry;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.gr4v1ty.supplylines.util.LogTags;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.MessageUtils;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * Manages Level 5 automatic restocking from remote Create network suppliers.
 * Evaluates restock policies periodically and requests items from suppliers
 * when local stock falls below target levels.
 */
public final class RestockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestockManager.class);

    @SuppressWarnings("unused")
    private final IColony colony;
    private long lastRestockCheckTick = Long.MIN_VALUE;

    /** Listener for order placement events (wired by BuildingStockKeeper) */
    @Nullable
    private Consumer<IncomingOrder> orderPlacedListener;

    /** Listener for statistics events (wired by BuildingStockKeeper) */
    @Nullable
    private Runnable orderCountListener;

    /**
     * Settings provider: default delivery time for ETA (wired by
     * BuildingStockKeeper)
     */
    @Nullable
    private IntSupplier defaultDeliveryProvider;

    /** Items that have pending orders - prevents duplicate orders */
    private final Set<ItemMatch.ItemStackKey> pendingOrderItems = new HashSet<>();

    /**
     * Represents an in-flight restock order for display purposes.
     */
    public static class RestockOrder implements IncomingOrder {
        private static final AtomicLong ORDER_COUNTER = new AtomicLong(0);

        public final long orderId;
        private final ItemStack item;
        private final int quantity;
        public final long requestedAtTick;
        private final int estimatedDeliveryTicks;
        public final UUID supplierNetworkId;

        public RestockOrder(ItemStack item, int quantity, long requestedAtTick, int estimatedDeliveryTicks,
                UUID supplierNetworkId) {
            this.orderId = ORDER_COUNTER.incrementAndGet();
            this.item = item.copy();
            this.quantity = quantity;
            this.requestedAtTick = requestedAtTick;
            this.estimatedDeliveryTicks = estimatedDeliveryTicks;
            this.supplierNetworkId = supplierNetworkId;
        }

        @Override
        public ItemStack getItem() {
            return item;
        }

        @Override
        public int getQuantity() {
            return quantity;
        }

        @Override
        public long getEstimatedArrivalTick() {
            return requestedAtTick + estimatedDeliveryTicks;
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
     * Sets the listener for order placement events.
     *
     * @param listener
     *            Consumer to be notified when orders are placed
     */
    public void setOrderPlacedListener(@Nullable Consumer<IncomingOrder> listener) {
        this.orderPlacedListener = listener;
    }

    /**
     * Sets the listener for statistics tracking (called once per order placed).
     *
     * @param listener
     *            Runnable to be notified when an order is placed
     */
    public void setOrderCountListener(@Nullable Runnable listener) {
        this.orderCountListener = listener;
    }

    /**
     * Sets the provider for default delivery time setting.
     *
     * @param provider
     *            Supplier returning the default delivery time in ticks
     */
    public void setDefaultDeliveryProvider(@Nullable IntSupplier provider) {
        this.defaultDeliveryProvider = provider;
    }

    /** Gets the default delivery time for ETA (from provider or global config) */
    private int getDefaultDeliveryTicks() {
        return defaultDeliveryProvider != null
                ? defaultDeliveryProvider.getAsInt()
                : ModConfig.SERVER.defaultDeliveryTicks.get();
    }

    /**
     * Called when an order is cleared (stock arrived, request completed, or
     * expired). Removes the item from the pending set so new orders can be placed.
     *
     * @param itemKey
     *            The item key that was cleared
     */
    public void onOrderCleared(ItemMatch.ItemStackKey itemKey) {
        pendingOrderItems.remove(itemKey);
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
     * @param restockIntervalTicks
     *            Interval between restock checks
     */
    public void processRestockPoliciesIfDue(Level level, RestockPolicyModule policyModule,
            SuppliersModule suppliersModule, NetworkIntegration localNetwork, int restockIntervalTicks) {

        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }

        // Check if restock evaluation is due
        if (lastRestockCheckTick != Long.MIN_VALUE && now - lastRestockCheckTick < restockIntervalTicks) {
            return;
        }
        lastRestockCheckTick = now;

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
                // Already at or above target
                continue;
            }

            // Skip if we already have an active order for this item
            ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(policyItem);
            if (pendingOrderItems.contains(itemKey)) {
                LOGGER.debug("{} Skipping {} - order already in flight", LogTags.ORDERING,
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
                // Fire events for each order (DisplayBoardManager tracks them)
                for (PendingRestockRequest req : requests) {
                    ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(req.item);
                    pendingOrderItems.add(itemKey);

                    RestockOrder newOrder = new RestockOrder(req.item, req.quantity, now, getDefaultDeliveryTicks(),
                            supplier.getNetworkId());
                    if (orderPlacedListener != null) {
                        orderPlacedListener.accept(newOrder);
                    }
                    // Track statistics
                    if (orderCountListener != null) {
                        orderCountListener.run();
                    }
                    LOGGER.debug("{} Order placed: {} x{} orderId={}", LogTags.ORDERING,
                            req.item.getDisplayName().getString(), req.quantity, newOrder.orderId);
                }

                // Log batched request
                LOGGER.debug("{} Batched {} item type(s) to supplier network {} (address: {}): {}", LogTags.ORDERING,
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

}

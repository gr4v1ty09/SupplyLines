package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule.SupplierEntry;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.gr4v1ty.supplylines.util.LogTags;
import com.gr4v1ty.supplylines.util.ResearchEffects;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.token.IToken;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Manages speculative ordering from remote Create network suppliers when colony
 * requests cannot be fulfilled locally. Unlike RestockManager which uses
 * player-defined policies, this reacts to actual colony demand.
 *
 * <p>
 * Key differences from RestockManager:
 * <ul>
 * <li>Reactive (demand-driven) vs. proactive (policy-driven)</li>
 * <li>Only queries suppliers with speculative ordering enabled</li>
 * <li>Requires a delay before ordering to allow normal fulfillment paths</li>
 * </ul>
 */
public final class SpeculativeOrderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpeculativeOrderManager.class);

    /** Gets the master switch for speculative ordering from config */
    private static boolean isSpeculativeOrderingEnabled() {
        return ModConfig.SERVER.enableSpeculativeOrdering.get();
    }

    /**
     * Checks if speculative ordering is unlocked via research for the given colony.
     *
     * @param colony
     *            The colony to check
     * @return true if the speculative ordering research has been completed
     */
    private static boolean isSpeculativeOrderingResearched(IColony colony) {
        return colony.getResearchManager().getResearchEffects()
                .getEffectStrength(ResearchEffects.SPECULATIVE_ORDERING) > 0;
    }

    /** Gets the delay before triggering speculative order from config */
    private static long getSpeculativeDelayTicks() {
        return ModConfig.SERVER.speculativeDelayTicks.get();
    }

    private final IColony colony;
    private long lastCheckTick = Long.MIN_VALUE;

    /**
     * Tracks unfulfilled requests: requestId -> UnfulfilledRequest. Uses
     * LinkedHashMap to maintain insertion order for FIFO processing.
     */
    private final Map<IToken<?>, UnfulfilledRequest> trackedRequests = new LinkedHashMap<>();

    /** Listener for order placement events (wired by BuildingStockKeeper) */
    @Nullable
    private Consumer<IncomingOrder> orderPlacedListener;

    /** Listener for request completion events (wired by BuildingStockKeeper) */
    @Nullable
    private Consumer<IToken<?>> requestCompletedListener;

    /**
     * Represents an unfulfilled colony request being tracked for speculative
     * ordering.
     */
    private static class UnfulfilledRequest {
        final IToken<?> requestId;
        final long firstSeenTick;
        final ItemStack item;
        final int quantity;
        boolean speculativeOrderPlaced;

        UnfulfilledRequest(IToken<?> requestId, long firstSeenTick, ItemStack item, int quantity) {
            this.requestId = requestId;
            this.firstSeenTick = firstSeenTick;
            this.item = item.copy();
            this.quantity = quantity;
            this.speculativeOrderPlaced = false;
        }
    }

    /**
     * Represents a speculative order that has been placed.
     */
    public static class SpeculativeOrder implements IncomingOrder {
        private static final AtomicLong ORDER_COUNTER = new AtomicLong(0);

        public final long orderId;
        private final ItemStack item;
        private final int quantity;
        public final long requestedAtTick;
        public final UUID supplierNetworkId;
        public final IToken<?> forRequestId;

        public SpeculativeOrder(ItemStack item, int quantity, long requestedAtTick, UUID supplierNetworkId,
                IToken<?> forRequestId) {
            this.orderId = ORDER_COUNTER.incrementAndGet();
            this.item = item.copy();
            this.quantity = quantity;
            this.requestedAtTick = requestedAtTick;
            this.supplierNetworkId = supplierNetworkId;
            this.forRequestId = forRequestId;
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
            return requestedAtTick + ModConfig.SERVER.defaultDeliveryTicks.get();
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

    public SpeculativeOrderManager(IColony colony) {
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
     * Sets the listener for request completion events.
     *
     * @param listener
     *            Consumer to be notified when requests complete
     */
    public void setRequestCompletedListener(@Nullable Consumer<IToken<?>> listener) {
        this.requestCompletedListener = listener;
    }

    /**
     * Main entry point - called from BuildingStockKeeper.serverTick().
     *
     * @param level
     *            The world
     * @param suppliersModule
     *            Module containing supplier networks
     * @param localNetwork
     *            Integration for querying local stock levels
     * @param checkIntervalTicks
     *            Interval between checks
     */
    public void processSpeculativeOrdersIfDue(Level level, SuppliersModule suppliersModule,
            NetworkIntegration localNetwork, int checkIntervalTicks) {

        // Master switch check
        if (!isSpeculativeOrderingEnabled()) {
            LOGGER.debug("{} Speculative ordering disabled by config", LogTags.ORDERING);
            return;
        }

        // Research unlock check
        if (!isSpeculativeOrderingResearched(colony)) {
            return;
        }

        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }

        // Check if evaluation is due
        if (lastCheckTick != Long.MIN_VALUE && now - lastCheckTick < checkIntervalTicks) {
            return;
        }
        lastCheckTick = now;

        // Check if any suppliers have speculative ordering enabled
        List<SupplierEntry> suppliers = suppliersModule.getSuppliers();
        long speculativeCount = suppliers.stream().filter(SupplierEntry::allowsSpeculativeOrders).count();
        LOGGER.debug("{} Speculative check: {} suppliers total, {} with speculative enabled", LogTags.ORDERING,
                suppliers.size(), speculativeCount);

        if (speculativeCount == 0) {
            // No need to track requests if no suppliers allow speculative ordering
            trackedRequests.clear();
            return;
        }

        // Phase 1: Scan colony for unfulfilled requests
        scanForUnfulfilledRequests(now);

        // Phase 2: Process requests that have waited long enough
        processDelayedRequests(level, suppliersModule, localNetwork, now);

        // Phase 3: Clean up completed/cancelled requests
        cleanupCompletedRequests();
    }

    /**
     * Scans the colony's request system for unfulfilled requests that SupplyLines
     * handles but cannot currently fulfill.
     */
    private void scanForUnfulfilledRequests(long now) {
        try {
            colony.getRequestManager().onColonyUpdate(request -> {
                // Track requests that are either:
                // - ASSIGNING: Looking for a resolver (no one can fulfill yet)
                // - IN_PROGRESS: Assigned to our resolver but items not yet available
                RequestState state = request.getState();
                if (state != RequestState.ASSIGNING && state != RequestState.IN_PROGRESS) {
                    // Request is fulfilled, cancelled, or in followup state
                    trackedRequests.remove(request.getId());
                    return false;
                }

                // Only track SupplyLines request types
                ItemStack item = extractItemFromRequest(request);
                if (item == null || item.isEmpty()) {
                    return false;
                }

                int quantity = extractQuantityFromRequest(request);
                if (quantity <= 0) {
                    return false;
                }

                // Track if not already tracked
                if (!trackedRequests.containsKey(request.getId())) {
                    trackedRequests.put(request.getId(), new UnfulfilledRequest(request.getId(), now, item, quantity));
                }

                return false; // Don't trigger reassignment
            });
        } catch (Exception e) {
            LOGGER.warn("{} Exception scanning for unfulfilled requests: {}", LogTags.ORDERING, e.getMessage());
        }
    }

    /**
     * Processes tracked requests that have waited longer than the configured delay.
     */
    private void processDelayedRequests(Level level, SuppliersModule suppliersModule, NetworkIntegration localNetwork,
            long now) {

        long delayTicks = getSpeculativeDelayTicks();
        int skippedAlreadyOrdered = 0;
        int skippedNotDelayedYet = 0;
        int eligibleForOrder = 0;

        for (UnfulfilledRequest req : trackedRequests.values()) {
            // Skip if we already placed a speculative order for this request
            if (req.speculativeOrderPlaced) {
                skippedAlreadyOrdered++;
                continue;
            }

            // Check if delay has passed
            long waitedTicks = now - req.firstSeenTick;
            if (waitedTicks < delayTicks) {
                skippedNotDelayedYet++;
                continue;
            }

            // Re-check if local stock is now available (race condition prevention)
            long localStock = localNetwork.getStockLevel(req.item);
            if (localStock >= req.quantity) {
                continue; // Will be cleaned up in cleanupCompletedRequests
            }

            eligibleForOrder++;

            // Try to find a speculative supplier
            SupplierResult result = findSpeculativeSupplier(req.item, req.quantity, suppliersModule);
            if (result != null) {
                placeSpeculativeOrder(level, result, req, now);
            }
        }

        if (eligibleForOrder > 0) {
            LOGGER.debug("{} Speculative check: {} eligible, {} waiting, {} already ordered", LogTags.ORDERING,
                    eligibleForOrder, skippedNotDelayedYet, skippedAlreadyOrdered);
        }
    }

    /**
     * Finds a supplier with speculative ordering enabled that has the requested
     * item in stock.
     */
    @Nullable
    private SupplierResult findSpeculativeSupplier(ItemStack item, int requiredQuantity,
            SuppliersModule suppliersModule) {

        ItemMatch.ItemStackKey itemKey = new ItemMatch.ItemStackKey(item);

        // Suppliers are already sorted by priority (0 = highest)
        for (SupplierEntry supplier : suppliersModule.getSuppliers()) {
            // Only check suppliers with speculative ordering enabled
            if (!supplier.allowsSpeculativeOrders()) {
                continue;
            }

            if (!supplier.hasValidAddress()) {
                continue;
            }

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
                    int quantity = available >= requiredQuantity
                            ? requiredQuantity
                            : (int) Math.min(available, Integer.MAX_VALUE);
                    return new SupplierResult(supplier, quantity);
                }
            } catch (Exception e) {
                LOGGER.warn("{} Failed to query supplier network: {}", LogTags.ORDERING, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Places a speculative order for an unfulfilled request.
     */
    private void placeSpeculativeOrder(Level level, SupplierResult result, UnfulfilledRequest req, long now) {
        SupplierEntry supplier = result.supplier;

        try {
            List<BigItemStack> orderedStacks = new ArrayList<>();
            orderedStacks.add(new BigItemStack(req.item.copy(), result.availableQuantity));

            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(orderedStacks);
            String destinationAddress = supplier.getRequestAddress();

            boolean success = LogisticsManager.broadcastPackageRequest(supplier.getNetworkId(),
                    LogisticallyLinkedBehaviour.RequestType.RESTOCK, order, null, destinationAddress);

            if (success) {
                req.speculativeOrderPlaced = true;

                SpeculativeOrder specOrder = new SpeculativeOrder(req.item, result.availableQuantity, now,
                        supplier.getNetworkId(), req.requestId);

                // Fire event (DisplayBoardManager tracks it)
                if (orderPlacedListener != null) {
                    orderPlacedListener.accept(specOrder);
                }

                String supplierLabel = supplier.getLabel().isEmpty()
                        ? supplier.getNetworkId().toString().substring(0, 8)
                        : supplier.getLabel();

                LOGGER.info("{} Speculative order placed: {} x{} from '{}' for request {}", LogTags.ORDERING,
                        req.item.getDisplayName().getString(), result.availableQuantity, supplierLabel, req.requestId);
            } else {
                LOGGER.warn("{} Failed to broadcast speculative order for {} to network {}", LogTags.ORDERING,
                        req.item.getDisplayName().getString(), supplier.getNetworkId());
            }
        } catch (Exception e) {
            LOGGER.error("{} Exception placing speculative order: {}", LogTags.ORDERING, e.getMessage());
        }
    }

    /**
     * Removes tracked requests that are no longer in ASSIGNING state. Notifies
     * DisplayBoardManager via requestCompletedListener for cleanup.
     */
    private void cleanupCompletedRequests() {
        Iterator<Map.Entry<IToken<?>, UnfulfilledRequest>> it = trackedRequests.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<IToken<?>, UnfulfilledRequest> entry = it.next();
            IToken<?> requestId = entry.getKey();
            UnfulfilledRequest req = entry.getValue();

            try {
                IRequest<?> request = colony.getRequestManager().getRequestForToken(requestId);
                if (request == null) {
                    it.remove();
                    notifyRequestCompleted(req, requestId);
                } else {
                    RequestState state = request.getState();
                    if (state != RequestState.ASSIGNING && state != RequestState.IN_PROGRESS) {
                        it.remove();
                        notifyRequestCompleted(req, requestId);
                    }
                }
            } catch (Exception e) {
                it.remove();
                notifyRequestCompleted(req, requestId);
            }
        }
    }

    /**
     * Notifies the request completed listener if an order was placed for this
     * request.
     */
    private void notifyRequestCompleted(UnfulfilledRequest req, IToken<?> requestId) {
        if (req.speculativeOrderPlaced && requestCompletedListener != null) {
            requestCompletedListener.accept(requestId);
        }
    }

    /**
     * Extracts the ItemStack from a request if it's a type SupplyLines handles.
     */
    @Nullable
    private ItemStack extractItemFromRequest(IRequest<?> request) {
        IRequestable requestable = request.getRequest();

        if (requestable instanceof Stack stack) {
            return stack.getStack();
        } else if (requestable instanceof Food) {
            // Food requests don't have a specific item - skip for now
            // Future: could match against any food item in supplier
            return null;
        } else if (requestable instanceof Burnable) {
            // Burnable requests don't have a specific item - skip for now
            return null;
        } else if (requestable instanceof Tool tool) {
            return tool.getResult();
        } else if (requestable instanceof RequestTag) {
            // Tag requests match multiple items - skip for now
            return null;
        } else if (requestable instanceof StackList stackList) {
            // StackList has multiple options - return first if available
            List<ItemStack> stacks = stackList.getStacks();
            return stacks != null && !stacks.isEmpty() ? stacks.get(0) : null;
        }

        return null;
    }

    /**
     * Extracts the required quantity from a request.
     */
    private int extractQuantityFromRequest(IRequest<?> request) {
        IRequestable requestable = request.getRequest();

        if (requestable instanceof Stack stack) {
            return stack.getCount();
        } else if (requestable instanceof Tool) {
            return 1;
        } else if (requestable instanceof StackList stackList) {
            return stackList.getCount();
        }

        return 1; // Default to 1 for other types
    }

    /**
     * Gets the count of currently tracked unfulfilled requests.
     */
    public int getTrackedRequestCount() {
        return trackedRequests.size();
    }
}

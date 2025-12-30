package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.model.StagingRequest;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.gr4v1ty.supplylines.util.inventory.RackPicker;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkIntegration.class);
    private static final String DELIVERY_FROGPORT_NAME = "SK_Deliveries";
    private static final long STAGING_TIMEOUT_TICKS = 1200L;
    private static final long BUFFER_WINDOW_TICKS = 60L;
    private final Map<IToken<?>, StagingRequest> pendingStagingRequests = new HashMap();
    private final Map<ItemMatch.ItemStackKey, Long> stockLevels = new HashMap<ItemMatch.ItemStackKey, Long>();
    private final Map<ItemMatch.ItemStackKey, Long> previousStockLevels = new HashMap<ItemMatch.ItemStackKey, Long>();
    private final Map<ItemMatch.ItemStackKey, Long> stockGauges = new HashMap<ItemMatch.ItemStackKey, Long>();
    private long lastStockSnapshotTick = Long.MIN_VALUE;
    private long lastStagingProcessTick = Long.MIN_VALUE;
    private final Map<IToken<?>, StagingRequest> bufferedRequests = new HashMap();
    private long lastBufferFlushTick = Long.MIN_VALUE;
    private final IColony colony;

    public NetworkIntegration(IColony colony) {
        this.colony = colony;
    }

    public void updateStockSnapshotIfDue(Level level, @Nullable BlockPos stockTickerPos, int stockSnapshotIntervalTicks,
            @Nullable Runnable stockChangeCallback) {
        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }
        if (this.lastStockSnapshotTick != Long.MIN_VALUE
                && now - this.lastStockSnapshotTick < (long) stockSnapshotIntervalTicks) {
            return;
        }
        this.lastStockSnapshotTick = now;
        if (stockTickerPos == null) {
            LOGGER.warn("{} No Stock Ticker position found - cannot update stock snapshot!", LogTags.INVENTORY);
            return;
        }
        BlockEntity be = level.getBlockEntity(stockTickerPos);
        if (!(be instanceof StockTickerBlockEntity)) {
            LOGGER.warn("{} Block at {} is not a StockTickerBlockEntity (actual={})", LogTags.INVENTORY, stockTickerPos,
                    (be != null ? be.getClass().getSimpleName() : "null"));
            return;
        }
        StockTickerBlockEntity ticker = (StockTickerBlockEntity) be;
        try {
            LogisticallyLinkedBehaviour behaviour = ticker.behaviour;
            if (behaviour == null || behaviour.freqId == null) {
                LOGGER.warn("{} Stock Ticker has no network frequency configured (behaviour={}, freqId={})",
                        LogTags.INVENTORY, (behaviour != null ? "found" : "null"),
                        behaviour != null ? behaviour.freqId : "N/A");
                return;
            }
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork((UUID) behaviour.freqId, (boolean) false);
            boolean stockChanged = false;
            HashMap<ItemMatch.ItemStackKey, Long> newStockLevels = new HashMap<ItemMatch.ItemStackKey, Long>();
            if (summary != null && !summary.isEmpty()) {
                List<BigItemStack> stacks = summary.getStacks();
                for (BigItemStack bigStack : stacks) {
                    if (bigStack == null || bigStack.stack == null)
                        continue;
                    ItemMatch.ItemStackKey key = new ItemMatch.ItemStackKey(bigStack.stack);
                    long existingQty = newStockLevels.getOrDefault(key, 0L);
                    newStockLevels.put(key, existingQty + (long) bigStack.count);
                }
            } else {
                LOGGER.warn(
                        "{} Network summary is empty or null - no items in network or frequency not configured properly",
                        LogTags.INVENTORY);
            }
            if (!this.previousStockLevels.isEmpty()) {
                for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.previousStockLevels.entrySet()) {
                    long newQty;
                    long oldQty = entry.getValue();
                    if (oldQty == (newQty = newStockLevels.getOrDefault(entry.getKey(), 0L).longValue()))
                        continue;
                    stockChanged = true;
                    break;
                }
                if (!stockChanged) {
                    for (ItemMatch.ItemStackKey key : newStockLevels.keySet()) {
                        if (this.previousStockLevels.containsKey(key))
                            continue;
                        stockChanged = true;
                        break;
                    }
                }
            } else {
                stockChanged = !newStockLevels.isEmpty();
            }
            this.previousStockLevels.clear();
            this.previousStockLevels.putAll(this.stockLevels);
            this.stockLevels.clear();
            this.stockLevels.putAll(newStockLevels);
            if (stockChanged && stockChangeCallback != null) {
                stockChangeCallback.run();
            }
        } catch (Exception e) {
            LOGGER.error("{} Failed to update stock snapshot from Stock Ticker", LogTags.INVENTORY, e);
        }
    }

    public void processStagingRequestsIfDue(Level level, @Nullable BlockPos stockTickerPos,
            List<BlockPos> stagingRackPositions, int stagingProcessIntervalTicks, Runnable reassignmentCallback) {
        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }
        if (this.lastStagingProcessTick != Long.MIN_VALUE
                && now - this.lastStagingProcessTick < (long) stagingProcessIntervalTicks) {
            return;
        }
        this.lastStagingProcessTick = now;
        this.processStagingRequests(level, stockTickerPos, stagingRackPositions, reassignmentCallback);
    }

    private void processStagingRequests(Level level, @Nullable BlockPos stockTickerPos,
            List<BlockPos> stagingRackPositions, Runnable reassignmentCallback) {
        this.flushBufferedRequestsIfDue(level);
        if (this.pendingStagingRequests.isEmpty()) {
            return;
        }
        if (stockTickerPos == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(stockTickerPos);
        if (!(be instanceof StockTickerBlockEntity)) {
            return;
        }
        StockTickerBlockEntity ticker = (StockTickerBlockEntity) be;
        LogisticallyLinkedBehaviour behaviour = ticker.behaviour;
        if (behaviour == null || behaviour.freqId == null) {
            LOGGER.error("{} Cannot process staging requests - Stock Ticker has no network frequency",
                    LogTags.DISPATCH);
            return;
        }
        Iterator<Map.Entry<IToken<?>, StagingRequest>> it = this.pendingStagingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IToken<?>, StagingRequest> entry = it.next();
            StagingRequest staging = entry.getValue();
            if (staging.state == StagingRequest.State.CANCELLED) {
                it.remove();
                continue;
            }
            if (staging.bundleLeaderId != null) {
                StagingRequest leader = this.pendingStagingRequests.get(staging.bundleLeaderId);
                if (leader == null) {
                    LOGGER.debug("{} Bundled follower's leader completed - removing follower", LogTags.DISPATCH);
                    it.remove();
                    continue;
                }
                if (leader.state == StagingRequest.State.BROADCASTED) {
                    staging.state = StagingRequest.State.BROADCASTED;
                    staging.broadcasted = true;
                }
                if (staging.state != StagingRequest.State.BROADCASTED
                        || !this.isAvailableInStaging(level, stagingRackPositions, staging.item, staging.quantity))
                    continue;
                staging.state = StagingRequest.State.COMPLETED;
                it.remove();
                reassignmentCallback.run();
                continue;
            }
            if (!staging.broadcasted || staging.state == StagingRequest.State.QUEUED) {
                boolean success = this.broadcastStagingRequest(level, behaviour.freqId, staging);
                if (!success)
                    continue;
                staging.broadcasted = true;
                staging.state = StagingRequest.State.BROADCASTED;
                continue;
            }
            if (staging.state != StagingRequest.State.BROADCASTED)
                continue;
            if (this.isAvailableInStaging(level, stagingRackPositions, staging.item, staging.quantity)) {
                staging.state = StagingRequest.State.COMPLETED;
                it.remove();
                reassignmentCallback.run();
                continue;
            }
            long elapsed = level.getGameTime() - staging.requestedAtTick;
            if (elapsed <= 1200L)
                continue;
            staging.state = StagingRequest.State.CANCELLED;
            it.remove();
        }
    }

    private void flushBufferedRequestsIfDue(Level level) {
        if (this.bufferedRequests.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }
        if (this.lastBufferFlushTick != Long.MIN_VALUE && now - this.lastBufferFlushTick < 60L) {
            return;
        }
        this.lastBufferFlushTick = now;
        if (this.bufferedRequests.isEmpty()) {
            return;
        }
        LOGGER.info("{} Flushing {} buffered requests to pending", LogTags.DISPATCH, this.bufferedRequests.size());
        ArrayList<StagingRequest> allRequests = new ArrayList<StagingRequest>(this.bufferedRequests.values());
        StagingRequest leader = (StagingRequest) allRequests.get(0);
        leader.bundleLeaderId = null;
        this.pendingStagingRequests.put(leader.parentRequestId, leader);
        for (int i = 1; i < allRequests.size(); ++i) {
            StagingRequest follower = (StagingRequest) allRequests.get(i);
            follower.bundleLeaderId = leader.parentRequestId;
            this.pendingStagingRequests.put(follower.parentRequestId, follower);
        }
        LOGGER.debug("{} Bundled {} requests into single package for broadcast", LogTags.DISPATCH, allRequests.size());
        this.bufferedRequests.clear();
        LOGGER.debug("{} After flush: {} pending requests", LogTags.DISPATCH, this.pendingStagingRequests.size());
    }

    private boolean broadcastStagingRequest(Level level, UUID freqId, StagingRequest staging) {
        try {
            ArrayList<BigItemStack> orderedStacks = new ArrayList<BigItemStack>();
            orderedStacks.add(new BigItemStack(staging.item.copy(), staging.quantity));
            if (staging.bundleLeaderId == null) {
                for (Map.Entry<IToken<?>, StagingRequest> entry : this.pendingStagingRequests.entrySet()) {
                    StagingRequest other = entry.getValue();
                    if (other.bundleLeaderId == null || !other.bundleLeaderId.equals(staging.parentRequestId))
                        continue;
                    orderedStacks.add(new BigItemStack(other.item.copy(), other.quantity));
                }
            }
            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(orderedStacks);
            String address = DELIVERY_FROGPORT_NAME;
            LOGGER.debug("{} Broadcasting package with {} item type(s) to Create network", LogTags.DISPATCH,
                    orderedStacks.size());
            boolean success = LogisticsManager.broadcastPackageRequest((UUID) freqId,
                    (LogisticallyLinkedBehaviour.RequestType) LogisticallyLinkedBehaviour.RequestType.RESTOCK,
                    (PackageOrderWithCrafts) order, null, (String) address);
            if (success) {
                LOGGER.debug("{} Successfully broadcast package with {} item type(s) to Create network",
                        LogTags.DISPATCH, orderedStacks.size());
            } else {
                LOGGER.error("{} Create network broadcast returned false - no packagers available?", LogTags.DISPATCH);
            }
            return success;
        } catch (Exception e) {
            LOGGER.error("{} Failed to broadcast staging request", LogTags.DISPATCH, e);
            return false;
        }
    }

    private boolean isAvailableInStaging(Level level, List<BlockPos> stagingRackPositions, ItemStack item,
            int quantity) {
        List<DeliveryPlanning.Pick> picks = RackPicker.pickFromRacks(level, this.colony, stagingRackPositions,
                new Stack(item, quantity, 1));
        int totalPicked = picks.stream().mapToInt(p -> p.count).sum();
        return totalPicked >= quantity;
    }

    public boolean requestFromStockNetwork(ItemStack item, int quantity, IToken<?> requestId, Level level) {
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} Cannot request from stock network - no stock data available", LogTags.ORDERING);
            return false;
        }
        long available = this.stockLevels.getOrDefault(new ItemMatch.ItemStackKey(item), 0L);
        if (available < (long) quantity) {
            return false;
        }
        StagingRequest staging = new StagingRequest();
        staging.item = item.copy();
        staging.quantity = quantity;
        staging.requestedAtTick = level.getGameTime();
        staging.broadcasted = false;
        staging.state = StagingRequest.State.QUEUED;
        staging.parentRequestId = requestId;
        this.bufferedRequests.put(requestId, staging);
        return true;
    }

    public boolean hasMatchingToolInNetwork(Tool toolRequest) {
        if (this.stockLevels.isEmpty()) {
            return false;
        }
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            ItemStack stack;
            ItemMatch.ItemStackKey key = entry.getKey();
            long quantity = entry.getValue();
            if (quantity <= 0L || (stack = key.toStack()).isDamaged() || !toolRequest.matches(stack))
                continue;
            return true;
        }
        return false;
    }

    public boolean requestToolFromStockNetwork(Tool toolRequest, IToken<?> requestId, Level level) {
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} Cannot request tool from stock network - no stock data available", LogTags.ORDERING);
            return false;
        }
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            ItemStack stack;
            ItemMatch.ItemStackKey key = entry.getKey();
            long quantity = entry.getValue();
            if (quantity <= 0L || (stack = key.toStack()).isDamaged() || !toolRequest.matches(stack))
                continue;
            StagingRequest staging = new StagingRequest();
            staging.item = stack.copy();
            staging.quantity = 1;
            staging.requestedAtTick = level.getGameTime();
            staging.broadcasted = false;
            staging.state = StagingRequest.State.QUEUED;
            staging.parentRequestId = requestId;
            this.bufferedRequests.put(requestId, staging);
            return true;
        }
        return false;
    }

    public long getStockLevelForTag(TagKey<Item> tag) {
        if (this.stockLevels.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            ItemStack stack;
            ItemMatch.ItemStackKey key = entry.getKey();
            long quantity = entry.getValue();
            if (quantity <= 0L || !(stack = key.toStack()).is(tag))
                continue;
            total += quantity;
        }
        return total;
    }

    public boolean requestFromStockNetworkByTag(TagKey<Item> tag, int quantity, IToken<?> requestId, Level level) {
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} Cannot request from stock network - no stock data available", LogTags.ORDERING);
            return false;
        }
        int remaining = quantity;
        ArrayList<StagingRequest> stagingRequests = new ArrayList<StagingRequest>();
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            long reserveThreshold;
            long availableAfterReserve;
            ItemStack stack;
            if (remaining <= 0)
                break;
            ItemMatch.ItemStackKey key = entry.getKey();
            long available = entry.getValue();
            if (available <= 0L || !(stack = key.toStack()).is(tag) || (availableAfterReserve = available
                    - (reserveThreshold = this.stockGauges.getOrDefault(key, 0L).longValue())) <= 0L)
                continue;
            int toTake = (int) Math.min((long) remaining, Math.min(availableAfterReserve, Integer.MAX_VALUE));
            StagingRequest staging = new StagingRequest();
            staging.item = stack.copy();
            staging.quantity = toTake;
            staging.requestedAtTick = level.getGameTime();
            staging.broadcasted = false;
            staging.state = StagingRequest.State.QUEUED;
            staging.parentRequestId = requestId;
            stagingRequests.add(staging);
            remaining -= toTake;
        }
        if (remaining > 0) {
            return false;
        }
        LOGGER.debug("{} Tag request {} creating {} sub-requests", LogTags.ORDERING, requestId, stagingRequests.size());
        for (StagingRequest staging : stagingRequests) {
            LOGGER.debug("{} Tag request {} buffering sub-request for {} x{}", LogTags.ORDERING, requestId,
                    staging.item.getDisplayName().getString(), staging.quantity);
            this.bufferedRequests.put(requestId, staging);
        }
        return true;
    }

    public long getStockLevelForStackList(StackList stackList) {
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} stockLevels is EMPTY - stock snapshot not taken yet or stock ticker has no data!",
                    LogTags.INVENTORY);
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            ItemStack stack;
            ItemMatch.ItemStackKey key = entry.getKey();
            long quantity = entry.getValue();
            if (quantity <= 0L || !stackList.matches(stack = key.toStack()))
                continue;
            total += quantity;
        }
        return total;
    }

    public boolean requestFromStockNetworkByStackList(StackList stackList, IToken<?> requestId, Level level) {
        int quantity;
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} Cannot request from stock network - no stock data available", LogTags.ORDERING);
            return false;
        }
        int remaining = quantity = stackList.getCount();
        ArrayList<StagingRequest> stagingRequests = new ArrayList<StagingRequest>();
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            long reserveThreshold;
            long availableAfterReserve;
            ItemStack stack;
            if (remaining <= 0)
                break;
            ItemMatch.ItemStackKey key = entry.getKey();
            long available = entry.getValue();
            if (available <= 0L || !stackList.matches(stack = key.toStack()) || (availableAfterReserve = available
                    - (reserveThreshold = this.stockGauges.getOrDefault(key, 0L).longValue())) <= 0L)
                continue;
            int toTake = (int) Math.min((long) remaining, Math.min(availableAfterReserve, Integer.MAX_VALUE));
            StagingRequest staging = new StagingRequest();
            staging.item = stack.copy();
            staging.quantity = toTake;
            staging.requestedAtTick = level.getGameTime();
            staging.broadcasted = false;
            staging.state = StagingRequest.State.QUEUED;
            staging.parentRequestId = requestId;
            stagingRequests.add(staging);
            remaining -= toTake;
        }
        if (remaining > 0) {
            return false;
        }
        LOGGER.debug("{} StackList request {} creating {} sub-requests", LogTags.ORDERING, requestId,
                stagingRequests.size());
        for (StagingRequest staging : stagingRequests) {
            LOGGER.debug("{} StackList request {} buffering sub-request for {} x{}", LogTags.ORDERING, requestId,
                    staging.item.getDisplayName().getString(), staging.quantity);
            this.bufferedRequests.put(requestId, staging);
        }
        return true;
    }

    public long getStockLevelForFood(Food food) {
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} stockLevels is EMPTY - stock snapshot not taken yet or stock ticker has no data!",
                    LogTags.INVENTORY);
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            ItemStack stack;
            ItemMatch.ItemStackKey key = entry.getKey();
            long quantity = entry.getValue();
            if (quantity <= 0L || !food.matches(stack = key.toStack()))
                continue;
            total += quantity;
        }
        return total;
    }

    public boolean requestFromStockNetworkForFood(Food food, IToken<?> requestId, Level level) {
        int quantity;
        if (this.stockLevels.isEmpty()) {
            LOGGER.warn("{} Cannot request from stock network - no stock data available", LogTags.ORDERING);
            return false;
        }
        int remaining = quantity = food.getCount();
        ArrayList<StagingRequest> stagingRequests = new ArrayList<StagingRequest>();
        for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : this.stockLevels.entrySet()) {
            long reserveThreshold;
            long availableAfterReserve;
            ItemStack stack;
            if (remaining <= 0)
                break;
            ItemMatch.ItemStackKey key = entry.getKey();
            long available = entry.getValue();
            if (available <= 0L || !food.matches(stack = key.toStack()) || (availableAfterReserve = available
                    - (reserveThreshold = this.stockGauges.getOrDefault(key, 0L).longValue())) <= 0L)
                continue;
            int toTake = (int) Math.min((long) remaining, Math.min(availableAfterReserve, Integer.MAX_VALUE));
            StagingRequest staging = new StagingRequest();
            staging.item = stack.copy();
            staging.quantity = toTake;
            staging.requestedAtTick = level.getGameTime();
            staging.broadcasted = false;
            staging.state = StagingRequest.State.QUEUED;
            staging.parentRequestId = requestId;
            stagingRequests.add(staging);
            remaining -= toTake;
        }
        if (remaining > 0) {
            return false;
        }
        LOGGER.debug("{} Food request {} creating {} sub-requests", LogTags.ORDERING, requestId,
                stagingRequests.size());
        for (StagingRequest staging : stagingRequests) {
            LOGGER.debug("{} Food request {} buffering sub-request for {} x{}", LogTags.ORDERING, requestId,
                    staging.item.getDisplayName().getString(), staging.quantity);
            this.bufferedRequests.put(requestId, staging);
        }
        return true;
    }

    public long getStockLevel(ItemStack item) {
        return this.stockLevels.getOrDefault(new ItemMatch.ItemStackKey(item), 0L);
    }

    public Map<ItemMatch.ItemStackKey, Long> getStockGauges() {
        return this.stockGauges;
    }

    public int getPendingRequestCount() {
        return this.pendingStagingRequests.size();
    }

    public boolean hasPendingStagingRequest(IToken<?> requestId) {
        return this.pendingStagingRequests.containsKey(requestId) || this.bufferedRequests.containsKey(requestId);
    }

    public boolean cancelStagingRequest(IToken<?> requestId) {
        StagingRequest buffered = this.bufferedRequests.remove(requestId);
        if (buffered != null) {
            LOGGER.debug("{} Cancelled buffered staging request for parent request {}", LogTags.ORDERING, requestId);
            return true;
        }
        StagingRequest staging = this.pendingStagingRequests.remove(requestId);
        if (staging != null) {
            staging.state = StagingRequest.State.CANCELLED;
            LOGGER.debug("{} Cancelled staging request for parent request {}", LogTags.ORDERING, requestId);
            return true;
        }
        return false;
    }

    public int cancelAllStagingRequestsForParent(IToken<?> parentRequestId) {
        int count = 0;
        Iterator<Map.Entry<IToken<?>, StagingRequest>> bufferedIt = this.bufferedRequests.entrySet().iterator();
        while (bufferedIt.hasNext()) {
            Map.Entry<IToken<?>, StagingRequest> entry = bufferedIt.next();
            StagingRequest staging = entry.getValue();
            if (staging.parentRequestId == null || !staging.parentRequestId.equals(parentRequestId))
                continue;
            bufferedIt.remove();
            ++count;
        }
        Iterator<Map.Entry<IToken<?>, StagingRequest>> it = this.pendingStagingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IToken<?>, StagingRequest> entry = it.next();
            StagingRequest staging = entry.getValue();
            if (staging.parentRequestId == null || !staging.parentRequestId.equals(parentRequestId))
                continue;
            staging.state = StagingRequest.State.CANCELLED;
            it.remove();
            ++count;
        }
        if (count > 0) {
            LOGGER.debug("{} Cancelled {} staging requests for parent request {}", LogTags.ORDERING, count,
                    parentRequestId);
        }
        return count;
    }
}

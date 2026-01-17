package com.gr4v1ty.supplylines.colony.buildings;

import com.gr4v1ty.supplylines.colony.manager.NetworkIntegration;
import com.gr4v1ty.supplylines.colony.manager.BuildingBlockScanner;
import com.gr4v1ty.supplylines.colony.manager.RequestHandler;
import com.gr4v1ty.supplylines.colony.manager.RestockManager;
import com.gr4v1ty.supplylines.colony.manager.SkillManager;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.colony.manager.migration.PanelMigrationManager;
import com.gr4v1ty.supplylines.colony.manager.migration.TrainStationMigrationManager;
import com.gr4v1ty.supplylines.colony.manager.migration.data.PanelMigrationData;
import com.gr4v1ty.supplylines.colony.manager.migration.data.TrainStationMigrationData;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.gr4v1ty.supplylines.util.RequestTypes;
import com.gr4v1ty.supplylines.util.inventory.InventoryOperations;
import com.gr4v1ty.supplylines.util.inventory.InventorySignature;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildingStockKeeper extends AbstractBuilding {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingStockKeeper.class);

    /** Gets the building level required for stock ticker functionality */
    public static int getStockTickerRequiredLevel() {
        return ModConfig.SERVER.stockTickerRequiredLevel.get();
    }

    /**
     * Gets the building level required for restock policy functionality (stock
     * ticker level + 1)
     */
    public static int getRestockPolicyRequiredLevel() {
        return getStockTickerRequiredLevel() + 1;
    }

    /** Gets the default interval for inventory signature refresh checks */
    private static int getDefaultInvSigIntervalTicks() {
        return ModConfig.SERVER.defaultInvSigIntervalTicks.get();
    }

    /** Gets the default interval for staging process when no skill manager */
    private static int getDefaultStagingProcessIntervalTicks() {
        return ModConfig.SERVER.defaultStagingProcessIntervalTicks.get();
    }

    /** Gets the default interval for rack rescan when no skill manager */
    private static int getDefaultRescanIntervalTicks() {
        return ModConfig.SERVER.defaultRescanIntervalTicks.get();
    }

    /**
     * Gets the default interval for stock snapshot updates when no skill manager
     */
    private static int getDefaultStockSnapshotIntervalTicks() {
        return ModConfig.SERVER.defaultStockSnapshotIntervalTicks.get();
    }

    /** Gets the default interval for restock policy checks when no skill manager */
    private static int getDefaultRestockIntervalTicks() {
        return ModConfig.SERVER.defaultRestockIntervalTicks.get();
    }

    /** NBT tag for storing pending panel migration data. */
    private static final String TAG_PENDING_MIGRATION = "pendingPanelMigration";

    /** NBT tag for storing pending station migration data. */
    private static final String TAG_PENDING_STATION_MIGRATION = "pendingStationMigration";

    private final BuildingBlockScanner blockScanner;
    private final NetworkIntegration networkIntegration;
    private SkillManager skillManager;
    private final RequestHandler requestHandler;
    private final RestockManager restockManager;
    private long lastInvSigTick = Long.MIN_VALUE;
    private long lastInvSig = Long.MIN_VALUE;

    /** Cached panel migration data during upgrade. Persisted to NBT. */
    @Nullable
    private PanelMigrationData pendingMigration = null;

    /** Cached station migration data during upgrade. Persisted to NBT. */
    @Nullable
    private TrainStationMigrationData pendingStationMigration = null;

    /** Flag to trigger worker patrol after order placement. */
    private boolean patrolRequested = false;

    public Map<ItemMatch.ItemStackKey, Long> getStockGauges() {
        return this.networkIntegration.getStockGauges();
    }

    /**
     * Returns the NetworkIntegration for direct access to stock network operations.
     * Prefer this over delegation methods for new code.
     */
    public NetworkIntegration getNetworkIntegration() {
        return this.networkIntegration;
    }

    public BuildingStockKeeper(IColony colony, BlockPos pos) {
        super(colony, pos);
        this.blockScanner = new BuildingBlockScanner(this);
        this.networkIntegration = new NetworkIntegration(colony);
        this.requestHandler = new RequestHandler(colony, pos);
        this.restockManager = new RestockManager(colony);
    }

    private void ensureRSRegistered(Level level) {
        List<BlockPos> racks = this.blockScanner.getRackPositions();
        this.requestHandler.ensureRSRegistered(level, racks, this);
    }

    @SuppressWarnings("deprecation")
    private void ensureSkillManagerInitialized() {
        WorkerBuildingModule workerModule;
        if (this.skillManager == null
                && (workerModule = this.getFirstModuleOccurance(WorkerBuildingModule.class)) != null) {
            this.skillManager = new SkillManager(workerModule);
        }
    }

    public String getBuildingDisplayName() {
        return "Stock Keeper";
    }

    public String getSchematicName() {
        return "stock_keeper_hut";
    }

    @Nullable
    public IItemHandler getStagingHandler(Level level) {
        return this.getStagingHandler(level, ItemStack.EMPTY);
    }

    @Nullable
    public BlockPos getSeatPos() {
        return this.blockScanner.getSeatPos();
    }

    @Nullable
    public BlockPos getStockTickerPos() {
        return this.blockScanner.getStockTickerPos();
    }

    @Nullable
    public BlockPos getDisplayBoardPos() {
        return this.blockScanner.getDisplayBoardPos();
    }

    public List<BlockPos> getBeltPositions() {
        return this.blockScanner.getBeltPositions();
    }

    /**
     * Requests a patrol to verify orders. Called when stock network orders are
     * placed. Multiple calls before patrol starts result in a single patrol
     * (idempotent).
     */
    public void requestPatrol() {
        LOGGER.debug("{} [SK] requestPatrol() called", LogTags.INVENTORY);
        this.patrolRequested = true;
    }

    /**
     * Consumes the patrol request flag. Returns true if patrol was requested, and
     * resets the flag to false.
     */
    public boolean consumePatrolRequest() {
        boolean result = this.patrolRequested;
        this.patrolRequested = false;
        return result;
    }

    @Nullable
    public IItemHandler getStagingHandler(Level level, @Nullable ItemStack exemplar) {
        IItemHandler face;
        if (level == null) {
            return null;
        }
        BlockPos pos = this.getPosition();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        IItemHandler h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (InventoryOperations.canAccept(h, exemplar)) {
            return h;
        }
        for (Direction side : Direction.values()) {
            face = be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (!InventoryOperations.canAccept(face, exemplar))
                continue;
            return face;
        }
        if (h != null) {
            return h;
        }
        for (Direction side : Direction.values()) {
            face = be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (face == null)
                continue;
            return face;
        }
        return null;
    }

    public void serverTick(Level level) {
        this.serverTick(level, false);
    }

    public void serverTick(Level level, boolean workerActive) {
        if (level == null || level.isClientSide()) {
            return;
        }
        this.scanIfDue(level);
        this.refreshInventorySignatureIfDue(level);
        this.ensureRSRegistered(level);
        if (this.getBuildingLevel() >= getStockTickerRequiredLevel() && workerActive) {
            this.updateStockSnapshotIfDue(level);
            this.processStagingRequestsIfDue(level);
        }
        if (this.getBuildingLevel() >= getRestockPolicyRequiredLevel() && workerActive) {
            this.processRestockPoliciesIfDue(level);
        }
    }

    @SuppressWarnings("deprecation")
    private void processRestockPoliciesIfDue(Level level) {
        RestockPolicyModule policyModule = this.getFirstModuleOccurance(RestockPolicyModule.class);
        SuppliersModule suppliersModule = this.getFirstModuleOccurance(SuppliersModule.class);

        if (policyModule == null || suppliersModule == null) {
            return;
        }

        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getRestockIntervalTicks()
                : getDefaultRestockIntervalTicks();

        this.restockManager.processRestockPoliciesIfDue(level, policyModule, suppliersModule, this.networkIntegration,
                this.blockScanner.getDisplayBoardPos(), interval);
    }

    private void processStagingRequestsIfDue(Level level) {
        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getStagingProcessIntervalTicks()
                : getDefaultStagingProcessIntervalTicks();
        this.networkIntegration.processStagingRequestsIfDue(level, this.blockScanner.getStockTickerPos(),
                this.blockScanner.getRackPositions(), interval, () -> this.refreshInventorySignatureIfDue(level));
    }

    public void scanIfDue(Level level) {

        @SuppressWarnings("unused")
        boolean racksChanged;

        IColony mcolony;
        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getRescanIntervalTicks()
                : getDefaultRescanIntervalTicks();
        if (this.blockScanner.isScanDue(level, interval)
                && (racksChanged = this.blockScanner.rescan(level, this.getBuildingLevel()))
                && (mcolony = this.getColony()) != null) {
            try {
                LOGGER.debug("{} scanIfDue: onColonyUpdate triggered (racksChanged=true)", LogTags.ORDERING);
                mcolony.getRequestManager().onColonyUpdate(req -> this.shouldReEvaluateRequest(req, null, "scanIfDue"));
            } catch (Exception e) {
                LOGGER.error("{} scanIfDue: onColonyUpdate threw", LogTags.ORDERING, e);
            }
        }
    }

    public boolean hasValidTargets(Level level) {
        return this.blockScanner.hasValidTargets(level);
    }

    public List<BlockPos> getRackPositions() {
        return this.blockScanner.getRackPositions();
    }

    /**
     * Evaluates whether a request should be re-evaluated by MineColonies. Used as a
     * predicate for onColonyUpdate calls.
     *
     * @param req
     *            The request to evaluate
     * @param toReassign
     *            Optional list to collect IN_PROGRESS request tokens for
     *            reassignment
     * @param logContext
     *            Context string for debug logging
     * @return true if the request should be re-evaluated
     */
    private boolean shouldReEvaluateRequest(IRequest<?> req, @Nullable List<IToken<?>> toReassign, String logContext) {
        boolean isOurType = RequestTypes.isSupplyLinesType(req.getRequest());
        if (!isOurType) {
            return false;
        }

        boolean hasChildren = req.hasChildren();
        RequestState state = req.getState();

        boolean result;
        if (hasChildren) {
            result = false; // Has active deliveries
        } else if (state == RequestState.FOLLOWUP_IN_PROGRESS) {
            result = false; // Waiting for deliveries to complete
        } else if (state == RequestState.IN_PROGRESS) {
            result = true; // Re-evaluate
            if (toReassign != null) {
                toReassign.add(req.getId());
            }
        } else if (state == RequestState.ASSIGNING) {
            result = true; // Looking for a resolver, we can help
        } else {
            result = false; // Not relevant state
        }

        if (result) {
            LOGGER.debug("{} {}: notifying request id={}, type={}, state={}", LogTags.ORDERING, logContext, req.getId(),
                    req.getRequest().getClass().getSimpleName(), state);
        }
        return result;
    }

    /**
     * Reassigns IN_PROGRESS requests so they get re-evaluated by MineColonies.
     *
     * @param mcolony
     *            The colony
     * @param toReassign
     *            List of request tokens to reassign
     * @param logContext
     *            Context string for debug logging
     */
    private void reassignRequests(IColony mcolony, List<IToken<?>> toReassign, String logContext) {
        for (IToken<?> token : toReassign) {
            try {
                if (mcolony.getRequestManager().getRequestForToken(token) != null) {
                    mcolony.getRequestManager().reassignRequest(token, Collections.emptyList());
                    LOGGER.debug("{} {}: reassigned IN_PROGRESS request {}", LogTags.ORDERING, logContext, token);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.debug("{} {}: request {} already resolved", LogTags.ORDERING, logContext, token);
            }
        }
    }

    private void refreshInventorySignatureIfDue(Level level) {
        IColony mcolony;
        long now = level.getGameTime();
        if (now <= 0L) {
            return;
        }
        if (this.lastInvSigTick != Long.MIN_VALUE && now - this.lastInvSigTick < getDefaultInvSigIntervalTicks()) {
            return;
        }
        long sig = this.computeInventorySignature(level);
        boolean changed = this.lastInvSig == Long.MIN_VALUE || sig != this.lastInvSig;
        long prevSig = this.lastInvSig;
        this.lastInvSig = sig;
        this.lastInvSigTick = now;
        if (changed && (mcolony = this.getColony()) != null) {
            try {
                LOGGER.debug("{} Inventory signature changed from {} to {}", LogTags.ORDERING, prevSig, sig);
                ArrayList<IToken<?>> toReassign = new ArrayList<>();
                mcolony.getRequestManager().onColonyUpdate(
                        req -> this.shouldReEvaluateRequest(req, toReassign, "refreshInventorySignatureIfDue"));
                this.reassignRequests(mcolony, toReassign, "refreshInventorySignatureIfDue");
            } catch (Exception e) {
                LOGGER.error("{} refreshInventorySignatureIfDue: onColonyUpdate threw", LogTags.ORDERING, e);
            }
        }
    }

    private long computeInventorySignature(Level level) {
        List<BlockPos> racksToHash = this.blockScanner.getRackPositions();
        IItemHandler staging = this.getStagingHandler(level);
        return InventorySignature.computeInventorySignature(level, racksToHash, staging, 0);
    }

    @Override
    public void onDestroyed() {
        this.unregisterRS(this.getColony().getWorld());
        super.onDestroyed();
    }

    private void unregisterRS(Level level) {
        this.requestHandler.unregisterRS(level);
    }

    public boolean hasStockTicker() {
        return this.getBuildingLevel() >= getStockTickerRequiredLevel()
                && this.blockScanner.getStockTickerPos() != null;
    }

    public long getStockLevel(ItemStack item) {
        return this.networkIntegration.getStockLevel(item);
    }

    public boolean requestFromStockNetwork(ItemStack item, int quantity, IToken<?> requestId) {
        if (this.blockScanner.getStockTickerPos() == null) {
            return false;
        }
        boolean result = this.networkIntegration.requestFromStockNetwork(item, quantity, requestId,
                this.getColony().getWorld());
        if (result) {
            this.requestPatrol();
        }
        return result;
    }

    public boolean hasMatchingToolInNetwork(Tool toolRequest) {
        return this.networkIntegration.hasMatchingToolInNetwork(toolRequest);
    }

    public boolean requestToolFromStockNetwork(Tool toolRequest, IToken<?> requestId) {
        if (this.blockScanner.getStockTickerPos() == null) {
            return false;
        }
        boolean result = this.networkIntegration.requestToolFromStockNetwork(toolRequest, requestId,
                this.getColony().getWorld());
        if (result) {
            this.requestPatrol();
        }
        return result;
    }

    public long getStockLevelForTag(TagKey<Item> tag) {
        return this.networkIntegration.getStockLevelForTag(tag);
    }

    public boolean requestFromStockNetworkByTag(TagKey<Item> tag, int quantity, IToken<?> requestId) {
        if (this.blockScanner.getStockTickerPos() == null) {
            return false;
        }
        boolean result = this.networkIntegration.requestFromStockNetworkByTag(tag, quantity, requestId,
                this.getColony().getWorld());
        if (result) {
            this.requestPatrol();
        }
        return result;
    }

    public long getStockLevelForStackList(StackList stackList) {
        return this.networkIntegration.getStockLevelForStackList(stackList);
    }

    public boolean requestFromStockNetworkByStackList(StackList stackList, IToken<?> requestId) {
        if (this.blockScanner.getStockTickerPos() == null) {
            return false;
        }
        boolean result = this.networkIntegration.requestFromStockNetworkByStackList(stackList, requestId,
                this.getColony().getWorld());
        if (result) {
            this.requestPatrol();
        }
        return result;
    }

    public long getStockLevelForFood(Food food) {
        return this.networkIntegration.getStockLevelForFood(food);
    }

    public boolean requestFromStockNetworkForFood(Food food, IToken<?> requestId) {
        if (this.blockScanner.getStockTickerPos() == null) {
            return false;
        }
        boolean result = this.networkIntegration.requestFromStockNetworkForFood(food, requestId,
                this.getColony().getWorld());
        if (result) {
            this.requestPatrol();
        }
        return result;
    }

    public long getStockLevelForBurnable(Burnable burnable) {
        return this.networkIntegration.getStockLevelForBurnable(burnable);
    }

    public boolean requestFromStockNetworkForBurnable(Burnable burnable, IToken<?> requestId) {
        if (this.blockScanner.getStockTickerPos() == null) {
            return false;
        }
        boolean result = this.networkIntegration.requestFromStockNetworkForBurnable(burnable, requestId,
                this.getColony().getWorld());
        if (result) {
            this.requestPatrol();
        }
        return result;
    }

    private void updateStockSnapshotIfDue(Level level) {
        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getStockSnapshotIntervalTicks()
                : getDefaultStockSnapshotIntervalTicks();
        this.networkIntegration.updateStockSnapshotIfDue(level, this.blockScanner.getStockTickerPos(), interval,
                (increases) -> {
                    // Notify RestockManager of stock increases to clear matching orders
                    for (Map.Entry<ItemMatch.ItemStackKey, Long> entry : increases.entrySet()) {
                        this.restockManager.onStockArrival(entry.getKey(), entry.getValue());
                    }
                    this.reassignPendingRequestsOnStockChange(level);
                });
    }

    /**
     * Called when stock network levels change. Notifies MineColonies to re-evaluate
     * ASSIGNING and IN_PROGRESS requests that we might now be able to fulfill.
     */
    private void reassignPendingRequestsOnStockChange(Level level) {
        IColony mcolony = this.getColony();
        if (mcolony == null) {
            return;
        }
        try {
            ArrayList<IToken<?>> toReassign = new ArrayList<>();
            mcolony.getRequestManager().onColonyUpdate(
                    req -> this.shouldReEvaluateRequest(req, toReassign, "reassignPendingRequestsOnStockChange"));
            this.reassignRequests(mcolony, toReassign, "reassignPendingRequestsOnStockChange");
        } catch (Exception e) {
            LOGGER.error("{} reassignPendingRequestsOnStockChange threw", LogTags.ORDERING, e);
        }
    }

    public boolean hasPendingStagingRequest(IToken<?> requestId) {
        return this.networkIntegration.hasPendingStagingRequest(requestId);
    }

    public void cancelStagingRequest(IToken<?> requestId) {
        this.networkIntegration.cancelStagingRequest(requestId);
    }

    public void awardWorkerSkillXP(double baseXp) {
        this.ensureSkillManagerInitialized();
        if (this.skillManager != null) {
            this.skillManager.awardWorkerSkillXP(baseXp);
        }
    }

    @Override
    public void requestUpgrade(final Player player, final BlockPos builder) {
        int currentLevel = this.getBuildingLevel();
        int nextLevel = currentLevel + 1;

        // Check if this is a 4->5 upgrade candidate
        boolean isLevel4To5 = (currentLevel == getStockTickerRequiredLevel()
                && nextLevel == getRestockPolicyRequiredLevel());

        // Check if work order already exists (would cause early return in
        // requestWorkOrder)
        boolean wasPendingConstruction = this.isPendingConstruction();

        // Call parent - may fail and return early
        super.requestUpgrade(player, builder);

        // Only scan if: it's 4->5, no prior work order, and now there IS a work order
        if (isLevel4To5 && !wasPendingConstruction && this.isPendingConstruction()) {
            Level level = this.getColony().getWorld();
            if (level != null && !level.isClientSide()) {
                this.pendingMigration = PanelMigrationManager.scanAndExtractPanelData(level, this.getCorners(),
                        currentLevel, nextLevel);
                if (this.pendingMigration != null) {
                    LOGGER.info("{} Upgrade to level 5 confirmed, cached {} panel configs", LogTags.MIGRATION,
                            this.pendingMigration.getPanels().size());
                    this.markDirty();
                }
            }
        }

        // Scan for station/postbox data on ANY upgrade (not just 4->5)
        if (!wasPendingConstruction && this.isPendingConstruction()) {
            Level level = this.getColony().getWorld();
            if (level != null && !level.isClientSide()) {
                this.pendingStationMigration = TrainStationMigrationManager.scanAndExtractData(level, this.getCorners(),
                        currentLevel, nextLevel);
                if (this.pendingStationMigration != null) {
                    LOGGER.info("{} Upgrade confirmed, cached {} station(s), {} postbox(es) for migration",
                            LogTags.MIGRATION, this.pendingStationMigration.getStations().size(),
                            this.pendingStationMigration.getPostboxes().size());
                    this.markDirty();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onUpgradeComplete(final int newLevel) {
        super.onUpgradeComplete(newLevel);

        // Check if we have pending migration data for this upgrade
        if (newLevel == getRestockPolicyRequiredLevel() && this.pendingMigration != null) {
            LOGGER.info("{} Upgrade to level {} complete, applying panel migration", LogTags.MIGRATION, newLevel);

            RestockPolicyModule policyModule = this.getFirstModuleOccurance(RestockPolicyModule.class);
            SuppliersModule suppliersModule = this.getFirstModuleOccurance(SuppliersModule.class);

            if (policyModule != null && suppliersModule != null) {
                int created = PanelMigrationManager.applyMigrationData(this.pendingMigration, policyModule,
                        suppliersModule);
                LOGGER.info("{} Applied {} policies from Factory Panel migration", LogTags.MIGRATION, created);
            } else {
                LOGGER.error("{} Cannot apply migration - modules not found!", LogTags.MIGRATION);
            }

            // Clear cached data
            this.pendingMigration = null;
            this.markDirty();
        }

        // Apply station migration (for any upgrade level)
        if (this.pendingStationMigration != null) {
            Level level = this.getColony().getWorld();
            if (level != null && !level.isClientSide()) {
                LOGGER.info("{} Upgrade to level {} complete, applying station migration", LogTags.MIGRATION, newLevel);

                TrainStationMigrationManager.MigrationResult result = TrainStationMigrationManager
                        .applyMigrationData(level, this.getCorners(), this.pendingStationMigration);

                LOGGER.info("{} Station migration complete: {} stations, {} postboxes restored", LogTags.MIGRATION,
                        result.stationsRestored, result.postboxesRestored);

                for (String warning : result.warnings) {
                    LOGGER.warn("{} {}", LogTags.MIGRATION, warning);
                }
            }

            // Clear cached data
            this.pendingStationMigration = null;
            this.markDirty();
        }
    }

    @Override
    public void deserializeNBT(final CompoundTag compound) {
        super.deserializeNBT(compound);

        if (compound.contains(TAG_PENDING_MIGRATION)) {
            this.pendingMigration = PanelMigrationData.fromNBT(compound.getCompound(TAG_PENDING_MIGRATION));
            LOGGER.info("{} Restored pending migration data ({} panels)", LogTags.MIGRATION,
                    this.pendingMigration.getPanels().size());
        }

        if (compound.contains(TAG_PENDING_STATION_MIGRATION)) {
            this.pendingStationMigration = TrainStationMigrationData
                    .fromNBT(compound.getCompound(TAG_PENDING_STATION_MIGRATION));
            LOGGER.info("{} Restored pending station migration data ({} stations, {} postboxes)", LogTags.MIGRATION,
                    this.pendingStationMigration.getStations().size(),
                    this.pendingStationMigration.getPostboxes().size());
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag compound = super.serializeNBT();

        if (this.pendingMigration != null) {
            compound.put(TAG_PENDING_MIGRATION, this.pendingMigration.toNBT());
        }

        if (this.pendingStationMigration != null) {
            compound.put(TAG_PENDING_STATION_MIGRATION, this.pendingStationMigration.toNBT());
        }

        return compound;
    }
}

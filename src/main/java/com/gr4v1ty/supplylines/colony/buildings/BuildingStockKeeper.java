package com.gr4v1ty.supplylines.colony.buildings;

import com.gr4v1ty.supplylines.colony.manager.NetworkIntegration;
import com.gr4v1ty.supplylines.colony.manager.RackManager;
import com.gr4v1ty.supplylines.colony.manager.RequestHandler;
import com.gr4v1ty.supplylines.colony.manager.SkillManager;
import com.gr4v1ty.supplylines.util.ItemMatch;
import com.gr4v1ty.supplylines.util.RequestTypes;
import com.gr4v1ty.supplylines.util.inventory.InventoryOperations;
import com.gr4v1ty.supplylines.util.inventory.InventorySignature;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
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
    private static final Random RANDOM = new Random();

    /** Building level required for stock ticker functionality */
    public static final int STOCK_TICKER_REQUIRED_LEVEL = 4;

    /** Default interval for inventory signature refresh checks */
    private static final int DEFAULT_INV_SIG_INTERVAL_TICKS = 40;
    /** Default interval for staging process when no skill manager */
    private static final int DEFAULT_STAGING_PROCESS_INTERVAL_TICKS = 60;
    /** Default interval for rack rescan when no skill manager */
    private static final int DEFAULT_RESCAN_INTERVAL_TICKS = 400;
    /** Default interval for stock snapshot updates when no skill manager */
    private static final int DEFAULT_STOCK_SNAPSHOT_INTERVAL_TICKS = 200;

    private final RackManager rackManager;
    private final NetworkIntegration networkIntegration;
    private SkillManager skillManager;
    private final RequestHandler requestHandler;
    private long lastInvSigTick = Long.MIN_VALUE;
    private long lastInvSig = Long.MIN_VALUE;

    public Map<ItemMatch.ItemStackKey, Long> getStockGauges() {
        return this.networkIntegration.getStockGauges();
    }

    public BuildingStockKeeper(IColony colony, BlockPos pos) {
        super(colony, pos);
        this.rackManager = new RackManager(this);
        this.networkIntegration = new NetworkIntegration(colony);
        this.requestHandler = new RequestHandler(colony, pos);
    }

    private void ensureRSRegistered(Level level) {
        List<BlockPos> racks = this.rackManager.getRackPositions();
        this.requestHandler.ensureRSRegistered(level, racks, this);
    }

    private void ensureSkillManagerInitialized() {
        WorkerBuildingModule workerModule;
        if (this.skillManager == null && (workerModule = (WorkerBuildingModule) this
                .getFirstModuleOccurance(WorkerBuildingModule.class)) != null) {
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
        return this.rackManager.getSeatPos();
    }

    @Nullable
    public BlockPos getStockTickerPos() {
        return this.rackManager.getStockTickerPos();
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
        IItemHandler h = (IItemHandler) be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (InventoryOperations.canAccept(h, exemplar)) {
            return h;
        }
        for (Direction side : Direction.values()) {
            face = (IItemHandler) be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (!InventoryOperations.canAccept(face, exemplar))
                continue;
            return face;
        }
        if (h != null) {
            return h;
        }
        for (Direction side : Direction.values()) {
            face = (IItemHandler) be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
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
        if (this.getBuildingLevel() >= STOCK_TICKER_REQUIRED_LEVEL && workerActive) {
            this.updateStockSnapshotIfDue(level);
            this.processStagingRequestsIfDue(level);
        }
    }

    private void processStagingRequestsIfDue(Level level) {
        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getStagingProcessIntervalTicks()
                : DEFAULT_STAGING_PROCESS_INTERVAL_TICKS;
        this.networkIntegration.processStagingRequestsIfDue(level, this.rackManager.getStockTickerPos(),
                this.rackManager.getRackPositions(), interval, () -> this.refreshInventorySignatureIfDue(level));
    }

    public void scanIfDue(Level level) {
        IColony mcolony;
        boolean racksChanged;
        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getRescanIntervalTicks()
                : DEFAULT_RESCAN_INTERVAL_TICKS;
        if (this.rackManager.isScanDue(level, interval)
                && (racksChanged = this.rackManager.rescan(level, this.getBuildingLevel()))
                && (mcolony = this.getColony()) != null) {
            try {
                LOGGER.info("{} [DEBUG] scanIfDue: onColonyUpdate triggered (racksChanged=true)", LogTags.ORDERING);
                mcolony.getRequestManager().onColonyUpdate(req -> this.shouldReEvaluateRequest(req, null, "scanIfDue"));
            } catch (Exception e) {
                LOGGER.error("{} [DEBUG] scanIfDue: onColonyUpdate threw", LogTags.ORDERING, e);
            }
        }
    }

    public boolean hasValidTargets(Level level) {
        return this.rackManager.hasValidTargets(level);
    }

    public List<BlockPos> getRackPositions() {
        return this.rackManager.getRackPositions();
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
        if (this.lastInvSigTick != Long.MIN_VALUE && now - this.lastInvSigTick < DEFAULT_INV_SIG_INTERVAL_TICKS) {
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
        List<BlockPos> racksToHash = this.rackManager.getRackPositions();
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
        return this.getBuildingLevel() >= STOCK_TICKER_REQUIRED_LEVEL && this.rackManager.getStockTickerPos() != null;
    }

    public long getStockLevel(ItemStack item) {
        return this.networkIntegration.getStockLevel(item);
    }

    public boolean requestFromStockNetwork(ItemStack item, int quantity, IToken<?> requestId) {
        if (this.rackManager.getStockTickerPos() == null) {
            return false;
        }
        return this.networkIntegration.requestFromStockNetwork(item, quantity, requestId, this.getColony().getWorld());
    }

    public boolean hasMatchingToolInNetwork(Tool toolRequest) {
        return this.networkIntegration.hasMatchingToolInNetwork(toolRequest);
    }

    public boolean requestToolFromStockNetwork(Tool toolRequest, IToken<?> requestId) {
        if (this.rackManager.getStockTickerPos() == null) {
            return false;
        }
        return this.networkIntegration.requestToolFromStockNetwork(toolRequest, requestId, this.getColony().getWorld());
    }

    public long getStockLevelForTag(TagKey<Item> tag) {
        return this.networkIntegration.getStockLevelForTag(tag);
    }

    public boolean requestFromStockNetworkByTag(TagKey<Item> tag, int quantity, IToken<?> requestId) {
        if (this.rackManager.getStockTickerPos() == null) {
            return false;
        }
        return this.networkIntegration.requestFromStockNetworkByTag(tag, quantity, requestId,
                this.getColony().getWorld());
    }

    public long getStockLevelForStackList(StackList stackList) {
        return this.networkIntegration.getStockLevelForStackList(stackList);
    }

    public boolean requestFromStockNetworkByStackList(StackList stackList, IToken<?> requestId) {
        if (this.rackManager.getStockTickerPos() == null) {
            return false;
        }
        return this.networkIntegration.requestFromStockNetworkByStackList(stackList, requestId,
                this.getColony().getWorld());
    }

    public long getStockLevelForFood(Food food) {
        return this.networkIntegration.getStockLevelForFood(food);
    }

    public boolean requestFromStockNetworkForFood(Food food, IToken<?> requestId) {
        if (this.rackManager.getStockTickerPos() == null) {
            return false;
        }
        return this.networkIntegration.requestFromStockNetworkForFood(food, requestId, this.getColony().getWorld());
    }

    private void updateStockSnapshotIfDue(Level level) {
        this.ensureSkillManagerInitialized();
        int interval = this.skillManager != null
                ? this.skillManager.getStockSnapshotIntervalTicks()
                : DEFAULT_STOCK_SNAPSHOT_INTERVAL_TICKS;
        this.networkIntegration.updateStockSnapshotIfDue(level, this.rackManager.getStockTickerPos(), interval,
                () -> this.reassignPendingRequestsOnStockChange(level));
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
        if (baseXp <= 0.0) {
            return;
        }
        WorkerBuildingModule worker = (WorkerBuildingModule) this.getFirstModuleOccurance(WorkerBuildingModule.class);
        if (worker == null) {
            return;
        }
        List<ICitizenData> assignedCitizens = worker.getAssignedCitizen();
        if (assignedCitizens.isEmpty()) {
            return;
        }
        ICitizenData citizen = assignedCitizens.get(0);
        if (citizen == null) {
            return;
        }
        ICitizenSkillHandler skillHandler = citizen.getCitizenSkillHandler();
        if (skillHandler == null) {
            return;
        }
        Skill skill = RANDOM.nextBoolean() ? Skill.Strength : Skill.Dexterity;
        skillHandler.addXpToSkill(skill, baseXp, citizen);
    }
}

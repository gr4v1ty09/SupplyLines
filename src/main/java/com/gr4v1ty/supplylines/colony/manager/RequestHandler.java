package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.ResolverAssemblyBuilder;
import com.gr4v1ty.supplylines.rs.location.IntakeLocation;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.FoodDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.StackDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.StackListDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.TagDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.ToolDeliveryVerifier;
import com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier;
import com.gr4v1ty.supplylines.util.RequestTypes;
import com.gr4v1ty.supplylines.util.inventory.RackPicker;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    // UUID prefixes for deterministic ID generation
    private static final String PROVIDER_PREFIX = "supplylines:provider:";
    private static final String STACK_RESOLVER_PREFIX = "supplylines:skresolver:";
    private static final String TOOL_RESOLVER_PREFIX = "supplylines:sktoolresolver:";
    private static final String TAG_RESOLVER_PREFIX = "supplylines:sktagresolver:";
    private static final String STACKLIST_RESOLVER_PREFIX = "supplylines:skstacklistresolver:";
    private static final String FOOD_RESOLVER_PREFIX = "supplylines:skfoodresolver:";

    private final UUID providerId;
    private final UUID skStackId;
    private final UUID skToolId;
    private final UUID skTagId;
    private final UUID skStackListId;
    private final UUID skFoodId;
    private final IColony colony;
    private final BlockPos buildingPosition;

    public RequestHandler(IColony colony, BlockPos buildingPosition) {
        this.colony = colony;
        this.buildingPosition = buildingPosition;
        String seed = (colony != null ? colony.getID() : 0) + "@" + buildingPosition.getX() + ","
                + buildingPosition.getY() + "," + buildingPosition.getZ();
        this.providerId = UUID.nameUUIDFromBytes((PROVIDER_PREFIX + seed).getBytes());
        this.skStackId = UUID.nameUUIDFromBytes((STACK_RESOLVER_PREFIX + seed).getBytes());
        this.skToolId = UUID.nameUUIDFromBytes((TOOL_RESOLVER_PREFIX + seed).getBytes());
        this.skTagId = UUID.nameUUIDFromBytes((TAG_RESOLVER_PREFIX + seed).getBytes());
        this.skStackListId = UUID.nameUUIDFromBytes((STACKLIST_RESOLVER_PREFIX + seed).getBytes());
        this.skFoodId = UUID.nameUUIDFromBytes((FOOD_RESOLVER_PREFIX + seed).getBytes());
    }

    /**
     * Ensures the provider is registered with MineColonies' request system. This
     * method is idempotent - safe to call every tick. MineColonies will tell us if
     * we're already registered (via IllegalArgumentException), so we don't need to
     * track registration state locally.
     */
    public void ensureRSRegistered(Level level, List<BlockPos> rackPositions, BuildingStockKeeper stockKeeperBuilding) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Always attempt registration - MineColonies will tell us if already done
        // This is idempotent and safe to call every tick
        IRequestManager rm = this.colony.getRequestManager();
        IntakeLocation anchor = new IntakeLocation((ResourceKey<Level>) this.colony.getDimension(),
                this.buildingPosition);
        Predicate<IRequester> consumerFilter = r -> true;
        Function<IRequester, ILocation> intakeLocator = requester -> {
            ILocation loc = requester.getLocation();
            return new IntakeLocation((ResourceKey<Level>) loc.getDimension(), loc.getInDimensionLocation());
        };
        Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> stackPicker = this.makeStackPicker(level,
                rackPositions);
        Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> toolPicker = this.makeToolPicker(level,
                rackPositions);
        Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> tagPicker = this.makeTagPicker(level,
                rackPositions);
        Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> stackListPicker = this
                .makeStackListPicker(level, rackPositions);
        Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> foodPicker = this.makeFoodPicker(level,
                rackPositions);
        StackDeliveryVerifier stackVerifier = (mgr, dest, parent) -> DeliveryVerifier.verifyDelivery(level, dest,
                (Stack) parent.getRequest());
        ToolDeliveryVerifier toolVerifier = (mgr, dest, parent) -> DeliveryVerifier.verifyToolDelivery(level, dest,
                (Tool) parent.getRequest());
        TagDeliveryVerifier tagVerifier = (mgr, dest, parent) -> DeliveryVerifier.verifyTagDelivery(level, dest,
                (RequestTag) parent.getRequest());
        StackListDeliveryVerifier stackListVerifier = (mgr, dest, parent) -> DeliveryVerifier
                .verifyStackListDelivery(level, dest, (StackList) parent.getRequest());
        FoodDeliveryVerifier foodVerifier = (mgr, dest, parent) -> DeliveryVerifier.verifyFoodDelivery(level, dest,
                (Food) parent.getRequest());

        // ResolverAssemblyBuilder.build() calls
        // SupplyLinesRequestSystem.registerProvider()
        // which is idempotent - catches IllegalArgumentException if already registered
        ResolverAssemblyBuilder.create(rm, this.providerId, anchor)
                .withStackResolver(this.skStackId, stackPicker, stackVerifier)
                .withToolResolver(this.skToolId, toolPicker, toolVerifier)
                .withTagResolver(this.skTagId, tagPicker, tagVerifier)
                .withStackListResolver(this.skStackListId, stackListPicker, stackListVerifier)
                .withFoodResolver(this.skFoodId, foodPicker, foodVerifier).consumerFilter(consumerFilter)
                .intakeLocator(intakeLocator).priority(80).build();
    }

    /**
     * Unregisters the provider from MineColonies' request system. Called when the
     * building is destroyed.
     */
    public void unregisterRS(Level level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        ResolverAssemblyBuilder.unregister(this.colony.getRequestManager(), this.providerId, this.skStackId,
                this.skToolId, this.skTagId, this.skStackListId, this.skFoodId);
    }

    public void triggerReassignment() {
        try {
            this.colony.getRequestManager().onColonyUpdate(req -> {
                boolean isOurType = RequestTypes.isSupplyLinesType(req.getRequest());
                boolean hasChildren = req.hasChildren();
                RequestState state = req.getState();

                // Return true for ASSIGNING and IN_PROGRESS requests
                // FOLLOWUP_IN_PROGRESS means waiting for deliveries, don't interrupt
                boolean result;
                if (isOurType && hasChildren) {
                    result = false; // Has active deliveries
                } else if (isOurType && state == RequestState.FOLLOWUP_IN_PROGRESS) {
                    result = false; // Waiting for deliveries to complete
                } else if (isOurType && state == RequestState.IN_PROGRESS) {
                    result = true; // Re-evaluate IN_PROGRESS requests
                } else if (isOurType && state == RequestState.ASSIGNING) {
                    result = true; // Looking for a resolver, we can help
                } else {
                    result = false; // Not our type or not relevant state
                }

                if (isOurType && result) {
                    LOGGER.debug("{} triggerReassignment: notifying request id={}, type={}, state={}", LogTags.ORDERING,
                            req.getId(), req.getRequest().getClass().getSimpleName(), state);
                }
                return result;
            });
        } catch (Throwable t) {
            LOGGER.error("{} triggerReassignment: onColonyUpdate threw", LogTags.ORDERING, t);
        }
    }

    private Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> makeStackPicker(Level level,
            List<BlockPos> rackPositions) {
        return parent -> {
            List source = rackPositions;
            return RackPicker.pickFromRacks(level, this.colony, source, (Stack) parent.getRequest());
        };
    }

    private Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> makeToolPicker(Level level,
            List<BlockPos> rackPositions) {
        return parent -> {
            List source = rackPositions;
            return RackPicker.pickToolFromRacks(level, this.colony, source, (Tool) parent.getRequest());
        };
    }

    private Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> makeTagPicker(Level level,
            List<BlockPos> rackPositions) {
        return parent -> {
            List source = rackPositions;
            return RackPicker.pickFromRacksByTag(level, this.colony, source, (RequestTag) parent.getRequest());
        };
    }

    private Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> makeStackListPicker(Level level,
            List<BlockPos> rackPositions) {
        return parent -> {
            List source = rackPositions;
            return RackPicker.pickFromRacksByStackList(level, this.colony, source, (StackList) parent.getRequest());
        };
    }

    private Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> makeFoodPicker(Level level,
            List<BlockPos> rackPositions) {
        return parent -> {
            List source = rackPositions;
            return RackPicker.pickFoodFromRacks(level, this.colony, source, (Food) parent.getRequest());
        };
    }

    public UUID getProviderId() {
        return this.providerId;
    }
}

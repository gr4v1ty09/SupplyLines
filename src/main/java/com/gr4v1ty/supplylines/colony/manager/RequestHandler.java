package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.rs.SupplyLinesRequestSystem;
import com.gr4v1ty.supplylines.rs.location.IntakeLocation;
import com.gr4v1ty.supplylines.rs.resolver.BurnableResolver;
import com.gr4v1ty.supplylines.rs.resolver.FoodResolver;
import com.gr4v1ty.supplylines.rs.resolver.ResolverComponents;
import com.gr4v1ty.supplylines.rs.resolver.StackListResolver;
import com.gr4v1ty.supplylines.rs.resolver.StackResolver;
import com.gr4v1ty.supplylines.rs.resolver.TagResolver;
import com.gr4v1ty.supplylines.rs.resolver.ToolResolver;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.util.LogTags;
import com.gr4v1ty.supplylines.util.RequestTypes;
import com.gr4v1ty.supplylines.util.inventory.RackPicker;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    // Tracks providers registered this session (cleared on world unload)
    private static final Set<UUID> registeredProviders = ConcurrentHashMap.newKeySet();

    // UUID prefixes for deterministic ID generation
    private static final String PROVIDER_PREFIX = "supplylines:provider:";
    private static final String STACK_RESOLVER_PREFIX = "supplylines:skresolver:";
    private static final String TOOL_RESOLVER_PREFIX = "supplylines:sktoolresolver:";
    private static final String TAG_RESOLVER_PREFIX = "supplylines:sktagresolver:";
    private static final String STACKLIST_RESOLVER_PREFIX = "supplylines:skstacklistresolver:";
    private static final String FOOD_RESOLVER_PREFIX = "supplylines:skfoodresolver:";
    private static final String BURNABLE_RESOLVER_PREFIX = "supplylines:skburnableresolver:";

    private final UUID providerId;
    private final UUID skStackId;
    private final UUID skToolId;
    private final UUID skTagId;
    private final UUID skStackListId;
    private final UUID skFoodId;
    private final UUID skBurnableId;
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
        this.skBurnableId = UUID.nameUUIDFromBytes((BURNABLE_RESOLVER_PREFIX + seed).getBytes());
    }

    /**
     * Ensures the provider is registered with MineColonies' request system. Uses a
     * static set to track registrations this session for fast-path return.
     */
    public void ensureRSRegistered(Level level, List<BlockPos> rackPositions, BuildingStockKeeper stockKeeperBuilding) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Fast path: already registered this session
        if (registeredProviders.contains(this.providerId)) {
            return;
        }

        IRequestManager rm = this.colony.getRequestManager();
        IntakeLocation anchor = new IntakeLocation(this.colony.getDimension(), this.buildingPosition);

        // Register components in static registry before creating resolvers
        registerComponents(level, rackPositions);

        // Create resolvers (they look up components from the static registry)
        List<IRequestResolver<?>> resolvers = List.of(new StackResolver(this.skStackId, anchor),
                new ToolResolver(this.skToolId, anchor), new TagResolver(this.skTagId, anchor),
                new StackListResolver(this.skStackListId, anchor), new FoodResolver(this.skFoodId, anchor),
                new BurnableResolver(this.skBurnableId, anchor));

        // Register with MineColonies
        SupplyLinesRequestSystem.registerProvider(rm, this.providerId, resolvers);
        registeredProviders.add(this.providerId);

        // MineColonies may have already called canResolveRequest() on deserialized
        // resolvers BEFORE components were ready (returned false). Now that components
        // are registered, trigger reassignment so MineColonies re-evaluates pending
        // requests.
        LOGGER.info("{} First registration complete, triggering reassignment", LogTags.ORDERING);
        this.triggerReassignment();
    }

    /**
     * Registers components for all resolver types in the static registry.
     */
    private void registerComponents(Level level, List<BlockPos> rackPositions) {
        Predicate<IRequester> filter = r -> true;
        Function<IRequester, ILocation> locator = requester -> {
            ILocation loc = requester.getLocation();
            return new IntakeLocation(loc.getDimension(), loc.getInDimensionLocation());
        };
        int priority = ModConfig.SERVER.resolverPriority.get();

        LOGGER.debug(
                "{} Registering components for 6 resolvers: stack={}, tool={}, tag={}, stacklist={}, food={}, burnable={}",
                LogTags.ORDERING, this.skStackId, this.skToolId, this.skTagId, this.skStackListId, this.skFoodId,
                this.skBurnableId);

        ResolverComponents.register(this.skStackId,
                new ResolverComponents<>(filter, priority, locator,
                        makePicker(level, rackPositions, RackPicker::pickFromRacks),
                        (mgr, dest, req) -> com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier
                                .verifyDelivery(level, dest, req.getRequest())));

        ResolverComponents.register(this.skToolId,
                new ResolverComponents<>(filter, priority, locator,
                        makePicker(level, rackPositions, RackPicker::pickToolFromRacks),
                        (mgr, dest, req) -> com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier
                                .verifyToolDelivery(level, dest, req.getRequest())));

        ResolverComponents.register(this.skTagId,
                new ResolverComponents<>(filter, priority, locator,
                        makePicker(level, rackPositions, RackPicker::pickFromRacksByTag),
                        (mgr, dest, req) -> com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier
                                .verifyTagDelivery(level, dest, req.getRequest())));

        ResolverComponents.register(this.skStackListId,
                new ResolverComponents<>(filter, priority, locator,
                        makePicker(level, rackPositions, RackPicker::pickFromRacksByStackList),
                        (mgr, dest, req) -> com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier
                                .verifyStackListDelivery(level, dest, req.getRequest())));

        ResolverComponents.register(this.skFoodId,
                new ResolverComponents<>(filter, priority, locator,
                        makePicker(level, rackPositions, RackPicker::pickFoodFromRacks),
                        (mgr, dest, req) -> com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier
                                .verifyFoodDelivery(level, dest, req.getRequest())));

        ResolverComponents.register(this.skBurnableId,
                new ResolverComponents<>(filter, priority, locator,
                        makePicker(level, rackPositions, RackPicker::pickBurnableFromRacks),
                        (mgr, dest, req) -> com.gr4v1ty.supplylines.util.inventory.DeliveryVerifier
                                .verifyBurnableDelivery(level, dest, req.getRequest())));
    }

    /**
     * Unregisters the provider from MineColonies' request system. Called when the
     * building is destroyed.
     */
    public void unregisterRS(Level level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Remove from tracking set
        registeredProviders.remove(this.providerId);

        // Unregister components from static registry
        ResolverComponents.unregister(this.skStackId);
        ResolverComponents.unregister(this.skToolId);
        ResolverComponents.unregister(this.skTagId);
        ResolverComponents.unregister(this.skStackListId);
        ResolverComponents.unregister(this.skFoodId);
        ResolverComponents.unregister(this.skBurnableId);

        // Unregister from MineColonies
        SupplyLinesRequestSystem.unregisterProvider(this.colony.getRequestManager(), this.providerId);
    }

    public void triggerReassignment() {
        try {
            this.colony.getRequestManager().onColonyUpdate(req -> {
                boolean isOurType = RequestTypes.isSupplyLinesType(req.getRequest());
                boolean hasChildren = req.hasChildren();
                RequestState state = req.getState();

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
        } catch (RuntimeException e) {
            LOGGER.error("{} triggerReassignment: onColonyUpdate threw", LogTags.ORDERING, e);
        }
    }

    /**
     * Generic picker factory method that eliminates duplication.
     */
    @FunctionalInterface
    private interface PickerFunction<T extends IRequestable> {
        List<DeliveryPlanning.Pick> pick(Level level, IColony colony, List<BlockPos> positions, T request);
    }

    private <T extends IRequestable> Function<IRequest<? extends T>, List<DeliveryPlanning.Pick>> makePicker(
            Level level, List<BlockPos> rackPositions, PickerFunction<T> pickMethod) {
        return parent -> pickMethod.pick(level, this.colony, rackPositions, parent.getRequest());
    }

    public UUID getProviderId() {
        return this.providerId;
    }

    /**
     * Clears the registration tracking set. Called on world unload.
     */
    public static void clearRegistrationTracking() {
        registeredProviders.clear();
        ResolverComponents.clear();
    }
}

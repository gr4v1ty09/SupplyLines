package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractResolver<T extends IRequestable> implements IRequestResolver<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResolver.class);
    private static final Set<IToken<?>> pendingStagingRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Reservation tracking - maps request ID to reserved picks to prevent race
    // conditions
    private static final Map<IToken<?>, List<DeliveryPlanning.Pick>> reservedPicks = new ConcurrentHashMap<>();
    protected final IToken<UUID> id;
    protected final ILocation location;

    protected AbstractResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        this.id = new StandardToken(Objects.requireNonNull(resolverId, "resolverId"));
        this.location = Objects.requireNonNull(resolverLocation, "resolverLocation");
    }

    @NotNull
    public IToken<?> getId() {
        return this.id;
    }

    @NotNull
    public ILocation getLocation() {
        return this.location;
    }

    @NotNull
    public abstract TypeToken<? extends T> getRequestType();

    /**
     * Gets the minimum count required to satisfy the request. Subclasses must
     * implement this based on their request type (Stack.getMinimumCount(), Tool=1,
     * etc.)
     */
    protected abstract int getRequiredCount(IRequest<? extends T> request);

    /**
     * Checks if the picks satisfy the request's minimum count requirement.
     */
    protected boolean picksSatisfyRequest(List<DeliveryPlanning.Pick> picks, IRequest<? extends T> request) {
        if (picks == null || picks.isEmpty()) {
            return false;
        }
        int totalPicked = picks.stream().mapToInt(p -> p.count).sum();
        int required = this.getRequiredCount(request);
        return totalPicked >= required;
    }

    public int getPriority() {
        return this.getComponentPriority();
    }

    public boolean isValid() {
        return this.hasComponents();
    }

    @Nullable
    protected BuildingStockKeeper getBuilding(IRequestManager manager) {
        try {
            IRequester building = manager.getColony()
                    .getRequesterBuildingForPosition(this.location.getInDimensionLocation());
            if (building instanceof BuildingStockKeeper) {
                BuildingStockKeeper sk = (BuildingStockKeeper) building;
                return sk;
            }
        } catch (Exception e) {
            LOGGER.debug("{} Failed to get building: {}", LogTags.ORDERING, e.getMessage());
        }
        return null;
    }

    public final boolean canResolveRequest(@NotNull IRequestManager manager, @NotNull IRequest<? extends T> request) {
        LOGGER.debug("{} canResolveRequest {} state={}", LogTags.ORDERING, request.getId(), request.getState());
        this.logCanResolveRequest(request);
        if (!this.hasComponents()) {
            LOGGER.warn("{} canResolveRequest: NO components for resolver {} - this should not happen!",
                    LogTags.ORDERING, this.id.getIdentifier());
            return false;
        }
        Predicate<IRequester> filter = this.getConsumerFilter();
        if (filter != null && !filter.test(request.getRequester())) {
            LOGGER.warn("{} canResolveRequest: requester filter FAILED for {} (requester={})", LogTags.ORDERING,
                    request.getId().toString(), request.getRequester().getClass().getSimpleName());
            return false;
        }
        List<DeliveryPlanning.Pick> picks = this.pickFromRacks(request);
        // Only return true if picks fully satisfy the request's minimum requirement
        if (this.picksSatisfyRequest(picks, request)) {
            return true;
        }
        // Check if items are available in the network (no side effects - staging
        // happens in attemptResolveRequest)
        BuildingStockKeeper building = this.getBuilding(manager);
        if (building != null) {
            boolean hasStockTicker = building.hasStockTicker();
            if (building.getBuildingLevel() >= BuildingStockKeeper.STOCK_TICKER_REQUIRED_LEVEL && hasStockTicker) {
                boolean availableInNetwork = this.isAvailableInNetwork(building, request);
                LOGGER.debug("{} canResolveRequest: isAvailableInNetwork={}", LogTags.ORDERING, availableInNetwork);
                if (availableInNetwork) {
                    // Just check availability - staging will be triggered in
                    // attemptResolveRequest()
                    return true;
                }
            } else {
                LOGGER.debug("{} canResolveRequest: Level 4+ requirements not met - level={}, hasStockTicker={}",
                        LogTags.ORDERING, building.getBuildingLevel(), hasStockTicker);
            }
        } else {
            LOGGER.warn("{} canResolveRequest: Building is NULL!", LogTags.ORDERING);
        }
        this.logCannotResolve(request);
        return false;
    }

    @Nullable
    public final List<IToken<?>> attemptResolveRequest(@NotNull IRequestManager manager,
            @NotNull IRequest<? extends T> request) {
        // Log the request state to debug why this is called repeatedly
        LOGGER.debug("{} attemptResolve {} state={}", LogTags.FULFILLMENT, request.getId(), request.getState());
        this.logAttemptResolveRequest(request);
        if (!this.hasComponents()) {
            LOGGER.error("{} No components registered for resolver {}", LogTags.ORDERING, this.id.getIdentifier());
            return ImmutableList.of();
        }
        ILocation dest = this.getIntakeLocation(request.getRequester());
        if (dest == null) {
            LOGGER.warn("{} No intake destination for requester: {}", LogTags.DELIVERY, request.getRequester());
            return ImmutableList.of();
        }
        LOGGER.debug("{} attemptResolveRequest: destination = {}", LogTags.DELIVERY, dest.getInDimensionLocation());
        List<DeliveryPlanning.Pick> picks = this.pickFromRacks(request);
        int requiredCount = this.getRequiredCount(request);
        int pickedCount = picks != null ? picks.stream().mapToInt(p -> p.count).sum() : 0;
        LOGGER.debug("{} attemptResolve {} - picks from racks: {} (total {} items, need {})", LogTags.DELIVERY,
                request.getId(), picks != null ? picks.size() : "null", pickedCount, requiredCount);

        // If items are available in racks AND satisfy the minimum requirement,
        // return empty list to signal we can handle this
        // Delivery children will be created in getFollowupRequestForCompletion()
        if (this.picksSatisfyRequest(picks, request)) {
            LOGGER.debug("{} attemptResolve {} - items available (picked {} >= required {}), returning empty list",
                    LogTags.FULFILLMENT, request.getId(), pickedCount, requiredCount);
            pendingStagingRequests.remove(request.getId());
            return ImmutableList.of();
        }

        // If staging is already pending, return null to tell MineColonies to try again
        // later
        if (pendingStagingRequests.contains(request.getId())) {
            LOGGER.debug("{} attemptResolve {} - returning null (staging pending)", LogTags.ORDERING, request.getId());
            return null;
        }

        // Items not in racks - check if we should trigger staging from network
        BuildingStockKeeper building = this.getBuilding(manager);
        if (building != null && building.getBuildingLevel() >= BuildingStockKeeper.STOCK_TICKER_REQUIRED_LEVEL
                && building.hasStockTicker()) {
            boolean availableInNetwork = this.isAvailableInNetwork(building, request);
            if (availableInNetwork) {
                pendingStagingRequests.add(request.getId());
                boolean staged = this.requestFromStockNetwork(building, request);
                if (staged) {
                    LOGGER.debug("{} attemptResolve {} - staging triggered, returning null to retry", LogTags.ORDERING,
                            request.getId());
                    return null; // Staging started, MineColonies will retry
                } else {
                    pendingStagingRequests.remove(request.getId());
                    LOGGER.warn("{} attemptResolve {} - failed to trigger staging", LogTags.ORDERING, request.getId());
                }
            }
        }

        LOGGER.debug("{} attemptResolve {} - items not in racks, cannot stage", LogTags.ORDERING, request.getId());
        this.logCannotFulfill(request);
        return null; // Tell MineColonies to try next resolver
    }

    public final void resolveRequest(@NotNull IRequestManager manager, @NotNull IRequest<? extends T> request) {
        LOGGER.debug("{} resolveRequest called for {}", LogTags.FULFILLMENT, request.getId());
        // Mark as RESOLVED - MineColonies will call getFollowupRequestForCompletion()
        // next
        manager.updateRequestState(request.getId(), RequestState.RESOLVED);
    }

    @Nullable
    @Override
    public List<IRequest<?>> getFollowupRequestForCompletion(@NotNull IRequestManager manager,
            @NotNull IRequest<? extends T> completedRequest) {
        LOGGER.debug("{} getFollowupRequestForCompletion called for {}", LogTags.FULFILLMENT, completedRequest.getId());

        ILocation dest = this.getIntakeLocation(completedRequest.getRequester());
        if (dest == null) {
            LOGGER.error("{} No intake destination for requester in getFollowupRequestForCompletion", LogTags.DELIVERY);
            reservedPicks.remove(completedRequest.getId()); // Clean up reservation
            return Collections.emptyList(); // Return empty list to complete without delivery
        }

        // Use reserved picks if available, otherwise fall back to pickFromRacks
        List<DeliveryPlanning.Pick> picks = reservedPicks.remove(completedRequest.getId());
        if (picks == null || picks.isEmpty()) {
            // No reservation - try to pick fresh (backwards compatibility / staging case)
            picks = this.pickFromRacks(completedRequest);
        }
        if (picks == null || picks.isEmpty()) {
            LOGGER.error("{} Items no longer available for resolved request {} - completing without delivery",
                    LogTags.FULFILLMENT, completedRequest.getId());
            return Collections.emptyList(); // Return empty list to complete gracefully
        }

        List<IRequest<?>> deliveries = new ArrayList<>();
        int priority = this.getComponentPriority();

        // Log pick details
        LOGGER.debug("{} Creating {} deliveries for request {}", LogTags.DELIVERY, picks.size(),
                completedRequest.getId());
        for (DeliveryPlanning.Pick pick : picks) {
            LOGGER.debug("{} Pick: {} x{} from {}", LogTags.DELIVERY,
                    pick.payload.getStack().getItem().getDescriptionId(), pick.count,
                    pick.source.getInDimensionLocation());
        }

        for (DeliveryPlanning.Pick pick : picks) {
            try {
                ItemStack stack = pick.payload.getStack().copy();
                stack.setCount(pick.count);

                LOGGER.debug("{} Delivery ItemStack: item={}, count={}, maxStackSize={}, sourceType={}",
                        LogTags.DELIVERY, stack.getItem().getDescriptionId(), stack.getCount(), stack.getMaxStackSize(),
                        pick.source.getClass().getSimpleName());

                IRequester destBuilding = manager.getColony()
                        .getRequesterBuildingForPosition(dest.getInDimensionLocation());
                if (destBuilding == null) {
                    LOGGER.error("{} Failed to get destination building for Delivery", LogTags.DELIVERY);
                    continue;
                }

                ILocation sourceLocation = pick.source;
                ILocation destLocation = destBuilding.getLocation();
                Delivery courierDelivery = new Delivery(sourceLocation, destLocation, stack, priority);

                // Use 'this' (the resolver) as the requester, like MineColonies does
                IToken<?> requestToken = manager.createRequest(this, courierDelivery);
                IRequest<?> deliveryRequest = manager.getRequestForToken(requestToken);
                if (deliveryRequest != null) {
                    deliveries.add(deliveryRequest);
                    LOGGER.debug("{} Created delivery child {} for parent {}", LogTags.DELIVERY, requestToken,
                            completedRequest.getId());
                }
            } catch (Exception e) {
                LOGGER.error("{} Failed to create Delivery request: {}", LogTags.DELIVERY, e.getMessage(), e);
            }
        }

        // Clean up staging state
        pendingStagingRequests.remove(completedRequest.getId());
        BuildingStockKeeper building = this.getBuilding(manager);
        if (building != null) {
            building.cancelStagingRequest(completedRequest.getId());
        }

        // Award XP for the completed request
        this.awardSkillXP(manager, completedRequest);

        // Log with source details for debugging
        if (!picks.isEmpty()) {
            StringBuilder sources = new StringBuilder();
            for (DeliveryPlanning.Pick pick : picks) {
                if (sources.length() > 0)
                    sources.append(", ");
                sources.append(pick.source.getInDimensionLocation().toShortString());
            }
            LOGGER.info("{} Fulfilled: {} - {} deliveries from [{}]", LogTags.FULFILLMENT,
                    this.getRequestDescription(completedRequest), deliveries.size(), sources);
        } else {
            LOGGER.info("{} Fulfilled: {} - {} deliveries created", LogTags.FULFILLMENT,
                    this.getRequestDescription(completedRequest), deliveries.size());
        }
        return deliveries.isEmpty() ? null : deliveries;
    }

    @NotNull
    protected final List<IToken<?>> createDeliveryChildren(@NotNull IRequestManager manager,
            @NotNull IRequest<? extends T> request, @NotNull List<DeliveryPlanning.Pick> picks,
            @NotNull ILocation dest) {
        // This method is no longer used - keeping for compatibility
        ArrayList<IToken<?>> children = new ArrayList<>(picks.size());
        int priority = this.getComponentPriority();
        for (DeliveryPlanning.Pick pick : picks) {
            try {
                ItemStack stack = pick.payload.getStack().copy();
                stack.setCount(pick.count);
                IRequester destBuilding = manager.getColony()
                        .getRequesterBuildingForPosition(dest.getInDimensionLocation());
                if (destBuilding == null) {
                    LOGGER.error("{} Failed to get destination building for Delivery child", LogTags.DELIVERY);
                    continue;
                }
                ILocation sourceLocation = pick.source;
                ILocation destLocation = destBuilding.getLocation();
                Delivery courierDelivery = new Delivery(sourceLocation, destLocation, stack, priority);
                // Use 'this' (the resolver) as the requester
                IToken<?> childToken = manager.createRequest(this, (IRequestable) courierDelivery);
                children.add(childToken);
            } catch (IllegalArgumentException e) {
                LOGGER.error("{} Failed to create Delivery child request: {}", LogTags.DELIVERY, e.getMessage(), e);
            }
        }
        return ImmutableList.copyOf(children);
    }

    protected void awardSkillXP(@NotNull IRequestManager manager, @NotNull IRequest<? extends T> request) {
        try {
            BuildingStockKeeper building = this.getBuilding(manager);
            if (building == null) {
                return;
            }
            double xp = this.calculateSkillXP(request);
            building.awardWorkerSkillXP(xp);
        } catch (Exception e) {
            LOGGER.debug("{} Failed to award skill XP for delivery: {}", LogTags.FULFILLMENT, e.getMessage());
        }
    }

    protected abstract boolean hasComponents();

    protected abstract int getComponentPriority();

    @Nullable
    protected abstract Predicate<IRequester> getConsumerFilter();

    @Nullable
    protected abstract ILocation getIntakeLocation(IRequester var1);

    @Nullable
    protected abstract List<DeliveryPlanning.Pick> pickFromRacks(IRequest<? extends T> var1);

    protected abstract boolean isAvailableInNetwork(BuildingStockKeeper var1, IRequest<? extends T> var2);

    protected abstract boolean requestFromStockNetwork(BuildingStockKeeper var1, IRequest<? extends T> var2);

    protected abstract boolean verifyDelivery(IRequestManager var1, ILocation var2, IRequest<? extends T> var3);

    protected abstract double calculateSkillXP(IRequest<? extends T> var1);

    protected abstract void logCanResolveRequest(IRequest<? extends T> var1);

    protected abstract void logCannotResolve(IRequest<? extends T> var1);

    protected abstract void logAttemptResolveRequest(IRequest<? extends T> var1);

    protected abstract void logCannotFulfill(IRequest<? extends T> var1);

    /**
     * Returns a short, human-readable description of the request for logging.
     * Subclasses should override to provide meaningful descriptions like "64x
     * Cobblestone" or "Tool: Pickaxe".
     */
    protected abstract String getRequestDescription(IRequest<? extends T> request);

    @Override
    public void onRequestAssigned(@NotNull IRequestManager manager, @NotNull IRequest<? extends T> request,
            boolean simulation) {
        if (simulation) {
            return; // Don't reserve during simulation
        }

        // Reserve picks for this request to prevent race conditions
        List<DeliveryPlanning.Pick> picks = this.pickFromRacks(request);
        String desc = this.getRequestDescription(request);
        if (picks != null && !picks.isEmpty()) {
            reservedPicks.put(request.getId(), new ArrayList<>(picks));
            LOGGER.info("{} Accepted: {} - reserved {} picks", LogTags.ORDERING, desc, picks.size());
        } else {
            LOGGER.info("{} Accepted: {} - awaiting staging from network", LogTags.ORDERING, desc);
        }
    }

    public void onAssignedRequestBeingCancelled(@NotNull IRequestManager m, @NotNull IRequest<? extends T> r) {
    }

    public void onAssignedRequestCancelled(@NotNull IRequestManager m, @NotNull IRequest<? extends T> r) {
        LOGGER.debug("{} onAssignedRequestCancelled: request {} was cancelled, state={}", LogTags.FULFILLMENT,
                r.getId().toString(), r.getState());
        BuildingStockKeeper building = this.getBuilding(m);
        if (building != null) {
            building.cancelStagingRequest(r.getId());
        }
        pendingStagingRequests.remove(r.getId());
        reservedPicks.remove(r.getId()); // Release any reserved picks
    }

    public void onRequestedRequestComplete(@NotNull IRequestManager m, @NotNull IRequest<?> r) {
        // Extract delivery info for user-friendly logging
        Object requestable = r.getRequest();
        if (requestable instanceof Delivery delivery) {
            ItemStack stack = delivery.getStack();
            LOGGER.info("{} Delivered: {}x {}", LogTags.DELIVERY, stack.getCount(), stack.getHoverName().getString());
        } else {
            LOGGER.debug("{} onRequestedRequestComplete: Child request {} completed", LogTags.FULFILLMENT,
                    r.getId().toString());
        }
    }

    public void onRequestedRequestCancelled(@NotNull IRequestManager m, @NotNull IRequest<?> r) {
        Object requestable = r.getRequest();
        if (requestable instanceof Delivery delivery) {
            ItemStack stack = delivery.getStack();
            LOGGER.info("{} Cancelled: {}x {} - source: {}, dest: {}", LogTags.DELIVERY, stack.getCount(),
                    stack.getHoverName().getString(), delivery.getStart().getInDimensionLocation().toShortString(),
                    delivery.getTarget().getInDimensionLocation().toShortString());
        } else {
            LOGGER.debug("{} onRequestedRequestCancelled: Child request {} was cancelled", LogTags.FULFILLMENT,
                    r.getId().toString());
        }
    }

    @NotNull
    public MutableComponent getRequesterDisplayName(@NotNull IRequestManager m, @NotNull IRequest<?> r) {
        return Component.literal((String) "Stock Keeper (SupplyLines)");
    }
}

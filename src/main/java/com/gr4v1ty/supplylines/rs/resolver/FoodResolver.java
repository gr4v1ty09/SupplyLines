package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.FoodDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FoodResolver extends AbstractResolver<Food> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FoodResolver.class);

    public FoodResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    public FoodResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation,
            @NotNull Predicate<IRequester> consumerFilter, int priority,
            @NotNull Function<IRequester, ILocation> intakeLocator,
            @NotNull Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> picker,
            @NotNull FoodDeliveryVerifier verifier) {
        super(resolverId, resolverLocation);
        ResolverComponentRegistry.registerFoodComponents(resolverId, new ResolverComponentRegistry.FoodComponents(
                consumerFilter, priority, intakeLocator, picker, verifier));
    }

    @Override
    @NotNull
    public TypeToken<? extends Food> getRequestType() {
        return TypeToken.of(Food.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Food> request) {
        // Food requests use getCount() - they don't have a separate minimumCount
        return ((Food) request.getRequest()).getCount();
    }

    @Override
    protected boolean hasComponents() {
        return this.getComponents() != null;
    }

    @Override
    protected int getComponentPriority() {
        ResolverComponentRegistry.FoodComponents comp = this.getComponents();
        return comp != null ? comp.priority : 0;
    }

    @Override
    @Nullable
    protected Predicate<IRequester> getConsumerFilter() {
        ResolverComponentRegistry.FoodComponents comp = this.getComponents();
        return comp != null ? comp.consumerFilter : null;
    }

    @Override
    @Nullable
    protected ILocation getIntakeLocation(IRequester requester) {
        ResolverComponentRegistry.FoodComponents comp = this.getComponents();
        return comp != null ? comp.intakeLocator.apply(requester) : null;
    }

    @Override
    @Nullable
    protected List<DeliveryPlanning.Pick> pickFromRacks(IRequest<? extends Food> request) {
        ResolverComponentRegistry.FoodComponents comp = this.getComponents();
        return comp != null ? comp.picker.apply(request) : null;
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Food> request) {
        long available = building.getStockLevelForFood((Food) request.getRequest());
        return available >= (long) ((Food) request.getRequest()).getCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Food> request) {
        return building.requestFromStockNetworkForFood((Food) request.getRequest(), request.getId());
    }

    @Override
    protected boolean verifyDelivery(IRequestManager manager, ILocation dest, IRequest<? extends Food> request) {
        ResolverComponentRegistry.FoodComponents comp = this.getComponents();
        return comp != null && comp.verifier.isDelivered(manager, dest, request);
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Food> request) {
        int count = ((Food) request.getRequest()).getCount();
        return 1.0 + Math.min((double) count / 16.0, 4.0);
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends Food> request) {
        LOGGER.debug("{} canResolveRequest: CALLED for request {} (state: {})", LogTags.ORDERING,
                request.getId().toString(), request.getState());
    }

    @Override
    protected void logCannotResolve(IRequest<? extends Food> request) {
        LOGGER.debug("{} canResolveRequest: NO for request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends Food> request) {
        LOGGER.debug("{} attemptResolveRequest: STARTING for request {} - food count: {}", LogTags.ORDERING,
                request.getId().toString(), ((Food) request.getRequest()).getCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends Food> request) {
        LOGGER.debug("{} No picks available for request {} (food)", LogTags.ORDERING, request.getId().toString());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Food> request) {
        Food food = (Food) request.getRequest();
        return food.getCount() + "x Food";
    }

    @Nullable
    private ResolverComponentRegistry.FoodComponents getComponents() {
        return ResolverComponentRegistry.getFoodComponents((UUID) this.id.getIdentifier());
    }
}

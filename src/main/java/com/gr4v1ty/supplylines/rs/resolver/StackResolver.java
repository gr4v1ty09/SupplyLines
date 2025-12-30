package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.StackDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
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

public final class StackResolver extends AbstractResolver<Stack> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackResolver.class);

    public StackResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    public StackResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation,
            @NotNull Predicate<IRequester> consumerFilter, int priority,
            @NotNull Function<IRequester, ILocation> intakeLocator,
            @NotNull Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> picker,
            @NotNull StackDeliveryVerifier verifier) {
        super(resolverId, resolverLocation);
        ResolverComponentRegistry.registerStackComponents(resolverId, new ResolverComponentRegistry.StackComponents(
                consumerFilter, priority, intakeLocator, picker, verifier));
    }

    @Override
    @NotNull
    public TypeToken<? extends Stack> getRequestType() {
        return TypeToken.of(Stack.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Stack> request) {
        Stack stackReq = (Stack) request.getRequest();
        return stackReq.getMinimumCount();
    }

    @Override
    protected boolean hasComponents() {
        return this.getComponents() != null;
    }

    @Override
    protected int getComponentPriority() {
        ResolverComponentRegistry.StackComponents comp = this.getComponents();
        return comp != null ? comp.priority : 0;
    }

    @Override
    @Nullable
    protected Predicate<IRequester> getConsumerFilter() {
        ResolverComponentRegistry.StackComponents comp = this.getComponents();
        return comp != null ? comp.consumerFilter : null;
    }

    @Override
    @Nullable
    protected ILocation getIntakeLocation(IRequester requester) {
        ResolverComponentRegistry.StackComponents comp = this.getComponents();
        return comp != null ? comp.intakeLocator.apply(requester) : null;
    }

    @Override
    @Nullable
    protected List<DeliveryPlanning.Pick> pickFromRacks(IRequest<? extends Stack> request) {
        ResolverComponentRegistry.StackComponents comp = this.getComponents();
        return comp != null ? comp.picker.apply(request) : null;
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Stack> request) {
        long available = building.getStockLevel(((Stack) request.getRequest()).getStack());
        return available >= (long) ((Stack) request.getRequest()).getCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Stack> request) {
        return building.requestFromStockNetwork(((Stack) request.getRequest()).getStack(),
                ((Stack) request.getRequest()).getCount(), request.getId());
    }

    @Override
    protected boolean verifyDelivery(IRequestManager manager, ILocation dest, IRequest<? extends Stack> request) {
        ResolverComponentRegistry.StackComponents comp = this.getComponents();
        return comp != null && comp.verifier.isDelivered(manager, dest, request);
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Stack> request) {
        int stackSize = ((Stack) request.getRequest()).getCount();
        return 1.0 + Math.min((double) stackSize / 16.0, 4.0);
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends Stack> request) {
        Stack stackReq = (Stack) request.getRequest();
        LOGGER.debug("{} Checking stack request: item={}, count={}, minCount={}, requester={}, state={}",
                LogTags.ORDERING, stackReq.getStack().getItem().getDescriptionId(), stackReq.getCount(),
                stackReq.getMinimumCount(), request.getRequester().getClass().getSimpleName(), request.getState());
    }

    @Override
    protected void logCannotResolve(IRequest<? extends Stack> request) {
        LOGGER.debug("{} canResolveRequest: NO for request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends Stack> request) {
        Stack stackReq = (Stack) request.getRequest();
        LOGGER.debug("{} Attempting stack request: item={}, count={}, minCount={}", LogTags.ORDERING,
                stackReq.getStack().getItem().getDescriptionId(), stackReq.getCount(), stackReq.getMinimumCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends Stack> request) {
        LOGGER.debug("{} No picks available for request {} (item: {})", LogTags.ORDERING, request.getId().toString(),
                ((Stack) request.getRequest()).getStack().getItem());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Stack> request) {
        Stack stackReq = (Stack) request.getRequest();
        return stackReq.getCount() + "x " + stackReq.getStack().getHoverName().getString();
    }

    @Nullable
    private ResolverComponentRegistry.StackComponents getComponents() {
        return ResolverComponentRegistry.getStackComponents((UUID) this.id.getIdentifier());
    }
}

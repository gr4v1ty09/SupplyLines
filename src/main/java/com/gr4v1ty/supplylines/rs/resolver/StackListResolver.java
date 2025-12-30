package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.StackListDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StackListResolver extends AbstractResolver<StackList> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackListResolver.class);

    public StackListResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    public StackListResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation,
            @NotNull Predicate<IRequester> consumerFilter, int priority,
            @NotNull Function<IRequester, ILocation> intakeLocator,
            @NotNull Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> picker,
            @NotNull StackListDeliveryVerifier verifier) {
        super(resolverId, resolverLocation);
        ResolverComponentRegistry.registerStackListComponents(resolverId,
                new ResolverComponentRegistry.StackListComponents(consumerFilter, priority, intakeLocator, picker,
                        verifier));
    }

    @Override
    @NotNull
    public TypeToken<? extends StackList> getRequestType() {
        return TypeToken.of(StackList.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends StackList> request) {
        StackList stackListReq = (StackList) request.getRequest();
        return stackListReq.getMinimumCount();
    }

    @Override
    protected boolean hasComponents() {
        return this.getComponents() != null;
    }

    @Override
    protected int getComponentPriority() {
        ResolverComponentRegistry.StackListComponents comp = this.getComponents();
        return comp != null ? comp.priority : 0;
    }

    @Override
    @Nullable
    protected Predicate<IRequester> getConsumerFilter() {
        ResolverComponentRegistry.StackListComponents comp = this.getComponents();
        return comp != null ? comp.consumerFilter : null;
    }

    @Override
    @Nullable
    protected ILocation getIntakeLocation(IRequester requester) {
        ResolverComponentRegistry.StackListComponents comp = this.getComponents();
        return comp != null ? comp.intakeLocator.apply(requester) : null;
    }

    @Override
    @Nullable
    protected List<DeliveryPlanning.Pick> pickFromRacks(IRequest<? extends StackList> request) {
        ResolverComponentRegistry.StackListComponents comp = this.getComponents();
        return comp != null ? comp.picker.apply(request) : null;
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends StackList> request) {
        long availableInNetwork = building.getStockLevelForStackList((StackList) request.getRequest());
        long required = ((StackList) request.getRequest()).getCount();
        LOGGER.debug("{} isAvailableInNetwork: StackList - required={}, availableInNetwork={}, items={}",
                LogTags.INVENTORY, required, availableInNetwork, ((StackList) request.getRequest()).getStacks().size());
        for (ItemStack stack : ((StackList) request.getRequest()).getStacks()) {
            LOGGER.debug("{} - Item: {} x{}", LogTags.INVENTORY, stack.getItem().getDescriptionId(), stack.getCount());
        }
        boolean result = availableInNetwork >= required;
        LOGGER.debug("{} isAvailableInNetwork: StackList result={} (available={} >= required={})", LogTags.INVENTORY,
                result, availableInNetwork, required);
        return result;
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends StackList> request) {
        return building.requestFromStockNetworkByStackList((StackList) request.getRequest(), request.getId());
    }

    @Override
    protected boolean verifyDelivery(IRequestManager manager, ILocation dest, IRequest<? extends StackList> request) {
        ResolverComponentRegistry.StackListComponents comp = this.getComponents();
        return comp != null && comp.verifier.isDelivered(manager, dest, request);
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends StackList> request) {
        int itemCount = ((StackList) request.getRequest()).getCount();
        int stackTypes = ((StackList) request.getRequest()).getStacks().size();
        return 2.0 + Math.min((double) itemCount / 16.0, 2.0) + Math.min((double) stackTypes / 4.0, 2.0);
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends StackList> request) {
        StackList stackList = (StackList) request.getRequest();
        LOGGER.debug("{} canResolveRequest: CALLED for request {} (state: {})", LogTags.ORDERING,
                request.getId().toString(), request.getState());
        LOGGER.debug("{} Checking stacklist request: items={}, count={}, requester={}, state={}", LogTags.ORDERING,
                stackList.getStacks().size(), stackList.getCount(), request.getRequester().getClass().getSimpleName(),
                request.getState());
        for (ItemStack stack : stackList.getStacks()) {
            LOGGER.debug("{} - Requested item: {} x{}", LogTags.ORDERING, stack.getItem().getDescriptionId(),
                    stack.getCount());
        }
    }

    @Override
    protected void logCannotResolve(IRequest<? extends StackList> request) {
        LOGGER.debug("{} canResolveRequest: NO for stacklist request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends StackList> request) {
        StackList stackList = (StackList) request.getRequest();
        LOGGER.debug("{} Attempting to resolve stacklist request: items={}, count={}, requester={}", LogTags.ORDERING,
                stackList.getStacks().size(), stackList.getCount(), request.getRequester().getClass().getSimpleName());
        for (ItemStack stack : stackList.getStacks()) {
            LOGGER.debug("{} - Requested item: {} x{}", LogTags.ORDERING, stack.getItem().getDescriptionId(),
                    stack.getCount());
        }
        LOGGER.debug("{} attemptResolveRequest: STARTING for stacklist request {} - items: {} x{}", LogTags.ORDERING,
                request.getId().toString(), stackList.getStacks().size(), stackList.getCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends StackList> request) {
        LOGGER.debug("{} No picks available for stacklist request {} (items: {})", LogTags.ORDERING,
                request.getId().toString(), ((StackList) request.getRequest()).getStacks().size());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends StackList> request) {
        StackList stackList = (StackList) request.getRequest();
        if (stackList.getStacks().isEmpty()) {
            return "StackList (empty)";
        }
        // Show first item as representative
        ItemStack first = stackList.getStacks().get(0);
        if (stackList.getStacks().size() == 1) {
            return stackList.getCount() + "x " + first.getHoverName().getString();
        }
        return stackList.getCount() + "x " + first.getHoverName().getString() + " (+"
                + (stackList.getStacks().size() - 1) + " more)";
    }

    @Nullable
    private ResolverComponentRegistry.StackListComponents getComponents() {
        return ResolverComponentRegistry.getStackListComponents((UUID) this.id.getIdentifier());
    }
}

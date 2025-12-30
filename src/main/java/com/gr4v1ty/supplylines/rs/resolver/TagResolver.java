package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.TagDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TagResolver extends AbstractResolver<RequestTag> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagResolver.class);

    public TagResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    public TagResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation,
            @NotNull Predicate<IRequester> consumerFilter, int priority,
            @NotNull Function<IRequester, ILocation> intakeLocator,
            @NotNull Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> picker,
            @NotNull TagDeliveryVerifier verifier) {
        super(resolverId, resolverLocation);
        ResolverComponentRegistry.registerTagComponents(resolverId,
                new ResolverComponentRegistry.TagComponents(consumerFilter, priority, intakeLocator, picker, verifier));
    }

    @Override
    @NotNull
    public TypeToken<? extends RequestTag> getRequestType() {
        return TypeToken.of(RequestTag.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends RequestTag> request) {
        RequestTag tagReq = (RequestTag) request.getRequest();
        return tagReq.getMinimumCount();
    }

    @Override
    protected boolean hasComponents() {
        return this.getComponents() != null;
    }

    @Override
    protected int getComponentPriority() {
        ResolverComponentRegistry.TagComponents comp = this.getComponents();
        return comp != null ? comp.priority : 0;
    }

    @Override
    @Nullable
    protected Predicate<IRequester> getConsumerFilter() {
        ResolverComponentRegistry.TagComponents comp = this.getComponents();
        return comp != null ? comp.consumerFilter : null;
    }

    @Override
    @Nullable
    protected ILocation getIntakeLocation(IRequester requester) {
        ResolverComponentRegistry.TagComponents comp = this.getComponents();
        return comp != null ? comp.intakeLocator.apply(requester) : null;
    }

    @Override
    @Nullable
    protected List<DeliveryPlanning.Pick> pickFromRacks(IRequest<? extends RequestTag> request) {
        ResolverComponentRegistry.TagComponents comp = this.getComponents();
        return comp != null ? comp.picker.apply(request) : null;
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends RequestTag> request) {
        long availableInNetwork = building
                .getStockLevelForTag((TagKey<Item>) ((RequestTag) request.getRequest()).getTag());
        return availableInNetwork >= (long) ((RequestTag) request.getRequest()).getCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends RequestTag> request) {
        return building.requestFromStockNetworkByTag((TagKey<Item>) ((RequestTag) request.getRequest()).getTag(),
                ((RequestTag) request.getRequest()).getCount(), request.getId());
    }

    @Override
    protected boolean verifyDelivery(IRequestManager manager, ILocation dest, IRequest<? extends RequestTag> request) {
        ResolverComponentRegistry.TagComponents comp = this.getComponents();
        return comp != null && comp.verifier.isDelivered(manager, dest, request);
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends RequestTag> request) {
        int itemCount = ((RequestTag) request.getRequest()).getCount();
        return 1.5 + Math.min((double) itemCount / 16.0, 4.0);
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends RequestTag> request) {
        LOGGER.debug("{} canResolveRequest: CALLED for request {} (state: {})", LogTags.ORDERING,
                request.getId().toString(), request.getState());
        LOGGER.debug("{} Checking tag request: tag={}, count={}, minCount={}, requester={}, state={}", LogTags.ORDERING,
                ((RequestTag) request.getRequest()).getTag().location(), ((RequestTag) request.getRequest()).getCount(),
                ((RequestTag) request.getRequest()).getMinimumCount(),
                request.getRequester().getClass().getSimpleName(), request.getState());
    }

    @Override
    protected void logCannotResolve(IRequest<? extends RequestTag> request) {
        LOGGER.debug("{} canResolveRequest: NO for tag request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends RequestTag> request) {
        LOGGER.debug("{} Attempting to resolve tag request: tag={}, count={}, requester={}", LogTags.ORDERING,
                ((RequestTag) request.getRequest()).getTag().location(), ((RequestTag) request.getRequest()).getCount(),
                request.getRequester().getClass().getSimpleName());
        LOGGER.debug("{} attemptResolveRequest: STARTING for tag request {} (tag: {}, count: {})", LogTags.ORDERING,
                request.getId().toString(), ((RequestTag) request.getRequest()).getTag().location(),
                ((RequestTag) request.getRequest()).getCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends RequestTag> request) {
        LOGGER.debug("{} No picks available for tag request {} (tag: {})", LogTags.ORDERING, request.getId().toString(),
                ((RequestTag) request.getRequest()).getTag().location());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends RequestTag> request) {
        RequestTag tagReq = (RequestTag) request.getRequest();
        return tagReq.getCount() + "x #" + tagReq.getTag().location().getPath();
    }

    @Nullable
    private ResolverComponentRegistry.TagComponents getComponents() {
        return ResolverComponentRegistry.getTagComponents((UUID) this.id.getIdentifier());
    }
}

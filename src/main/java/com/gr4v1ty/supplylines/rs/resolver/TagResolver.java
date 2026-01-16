package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.util.LogTags;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TagResolver extends AbstractResolver<RequestTag> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagResolver.class);

    public TagResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    @Override
    @NotNull
    public TypeToken<? extends RequestTag> getRequestType() {
        return TypeToken.of(RequestTag.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends RequestTag> request) {
        return request.getRequest().getMinimumCount();
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends RequestTag> request) {
        RequestTag tagReq = request.getRequest();
        long availableInNetwork = building.getStockLevelForTag(tagReq.getTag());
        return availableInNetwork >= (long) tagReq.getCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends RequestTag> request) {
        RequestTag tagReq = request.getRequest();
        return building.requestFromStockNetworkByTag(tagReq.getTag(), tagReq.getCount(), request.getId());
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends RequestTag> request) {
        int itemCount = request.getRequest().getCount();
        double base = ModConfig.SERVER.tagXpBase.get();
        double divisor = ModConfig.SERVER.tagXpDivisor.get();
        double cap = ModConfig.SERVER.tagXpCap.get();
        return base + Math.min((double) itemCount / divisor, cap);
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends RequestTag> request) {
        RequestTag tagReq = request.getRequest();
        LOGGER.debug("{} canResolveRequest: CALLED for request {} (state: {})", LogTags.ORDERING,
                request.getId().toString(), request.getState());
        LOGGER.debug("{} Checking tag request: tag={}, count={}, minCount={}, requester={}, state={}", LogTags.ORDERING,
                tagReq.getTag().location(), tagReq.getCount(), tagReq.getMinimumCount(),
                request.getRequester().getClass().getSimpleName(), request.getState());
    }

    @Override
    protected void logCannotResolve(IRequest<? extends RequestTag> request) {
        LOGGER.debug("{} canResolveRequest: NO for tag request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends RequestTag> request) {
        RequestTag tagReq = request.getRequest();
        LOGGER.debug("{} Attempting to resolve tag request: tag={}, count={}, requester={}", LogTags.ORDERING,
                tagReq.getTag().location(), tagReq.getCount(), request.getRequester().getClass().getSimpleName());
        LOGGER.debug("{} attemptResolveRequest: STARTING for tag request {} (tag: {}, count: {})", LogTags.ORDERING,
                request.getId().toString(), tagReq.getTag().location(), tagReq.getCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends RequestTag> request) {
        LOGGER.debug("{} No picks available for tag request {} (tag: {})", LogTags.ORDERING, request.getId().toString(),
                request.getRequest().getTag().location());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends RequestTag> request) {
        RequestTag tagReq = request.getRequest();
        return tagReq.getCount() + "x #" + tagReq.getTag().location().getPath();
    }
}

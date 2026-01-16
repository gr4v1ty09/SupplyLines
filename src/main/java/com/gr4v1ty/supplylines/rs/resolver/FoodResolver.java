package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.util.LogTags;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FoodResolver extends AbstractResolver<Food> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FoodResolver.class);

    public FoodResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    @Override
    @NotNull
    public TypeToken<? extends Food> getRequestType() {
        return TypeToken.of(Food.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Food> request) {
        return request.getRequest().getCount();
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Food> request) {
        Food foodReq = request.getRequest();
        long available = building.getStockLevelForFood(foodReq);
        return available >= (long) foodReq.getCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Food> request) {
        return building.requestFromStockNetworkForFood(request.getRequest(), request.getId());
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Food> request) {
        int count = request.getRequest().getCount();
        double base = ModConfig.SERVER.stackXpBase.get();
        double divisor = ModConfig.SERVER.stackXpDivisor.get();
        double cap = ModConfig.SERVER.stackXpCap.get();
        return base + Math.min((double) count / divisor, cap);
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
                request.getId().toString(), request.getRequest().getCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends Food> request) {
        LOGGER.debug("{} No picks available for request {} (food)", LogTags.ORDERING, request.getId().toString());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Food> request) {
        return request.getRequest().getCount() + "x Food";
    }
}

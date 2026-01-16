package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.gr4v1ty.supplylines.util.LogTags;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BurnableResolver extends AbstractResolver<Burnable> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BurnableResolver.class);

    public BurnableResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    @Override
    @NotNull
    public TypeToken<? extends Burnable> getRequestType() {
        return TypeToken.of(Burnable.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Burnable> request) {
        return request.getRequest().getCount();
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Burnable> request) {
        Burnable burnableReq = request.getRequest();
        long available = building.getStockLevelForBurnable(burnableReq);
        return available >= (long) burnableReq.getCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Burnable> request) {
        return building.requestFromStockNetworkForBurnable(request.getRequest(), request.getId());
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Burnable> request) {
        int count = request.getRequest().getCount();
        double base = ModConfig.SERVER.stackXpBase.get();
        double divisor = ModConfig.SERVER.stackXpDivisor.get();
        double cap = ModConfig.SERVER.stackXpCap.get();
        return base + Math.min((double) count / divisor, cap);
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends Burnable> request) {
        LOGGER.debug("{} canResolveRequest: CALLED for request {} (state: {})", LogTags.ORDERING,
                request.getId().toString(), request.getState());
    }

    @Override
    protected void logCannotResolve(IRequest<? extends Burnable> request) {
        LOGGER.debug("{} canResolveRequest: NO for request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends Burnable> request) {
        LOGGER.debug("{} attemptResolveRequest: STARTING for request {} - burnable count: {}", LogTags.ORDERING,
                request.getId().toString(), request.getRequest().getCount());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends Burnable> request) {
        LOGGER.debug("{} No picks available for request {} (burnable)", LogTags.ORDERING, request.getId().toString());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Burnable> request) {
        return request.getRequest().getCount() + "x Fuel";
    }
}

package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class BurnableResolver extends AbstractResolver<Burnable> {
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
    protected String getRequestDescription(IRequest<? extends Burnable> request) {
        return request.getRequest().getCount() + "x Fuel";
    }
}

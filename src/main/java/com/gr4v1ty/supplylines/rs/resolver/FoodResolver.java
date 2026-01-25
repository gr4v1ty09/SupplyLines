package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class FoodResolver extends AbstractResolver<Food> {
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
    protected String getRequestDescription(IRequest<? extends Food> request) {
        return request.getRequest().getCount() + "x Food";
    }
}

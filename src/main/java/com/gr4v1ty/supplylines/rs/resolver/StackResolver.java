package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class StackResolver extends AbstractResolver<Stack> {
    public StackResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    @Override
    @NotNull
    public TypeToken<? extends Stack> getRequestType() {
        return TypeToken.of(Stack.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Stack> request) {
        return request.getRequest().getMinimumCount();
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Stack> request) {
        Stack stackReq = request.getRequest();
        long available = building.getStockLevel(stackReq.getStack());
        // Use minCount for partial fulfillment - accept if we have at least the minimum
        return available >= (long) stackReq.getMinimumCount();
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Stack> request) {
        Stack stackReq = request.getRequest();
        long available = building.getStockLevel(stackReq.getStack());
        // Request the lesser of what's available or what's requested (partial
        // fulfillment)
        int requestQty = (int) Math.min(available, stackReq.getCount());
        return building.requestFromStockNetwork(stackReq.getStack(), requestQty, request.getId());
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Stack> request) {
        int stackSize = request.getRequest().getCount();
        double base = ModConfig.SERVER.stackXpBase.get();
        double divisor = ModConfig.SERVER.stackXpDivisor.get();
        double cap = ModConfig.SERVER.stackXpCap.get();
        return base + Math.min((double) stackSize / divisor, cap);
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Stack> request) {
        Stack stackReq = request.getRequest();
        return stackReq.getCount() + "x " + stackReq.getStack().getHoverName().getString();
    }
}

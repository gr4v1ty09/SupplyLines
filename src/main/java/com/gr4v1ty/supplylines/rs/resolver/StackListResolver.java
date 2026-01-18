package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class StackListResolver extends AbstractResolver<StackList> {
    public StackListResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    @Override
    @NotNull
    public TypeToken<? extends StackList> getRequestType() {
        return TypeToken.of(StackList.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends StackList> request) {
        return request.getRequest().getMinimumCount();
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends StackList> request) {
        StackList stackListReq = request.getRequest();
        long availableInNetwork = building.getStockLevelForStackList(stackListReq);
        long required = stackListReq.getMinimumCount();
        return availableInNetwork >= required;
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends StackList> request) {
        return building.requestFromStockNetworkByStackList(request.getRequest(), request.getId());
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends StackList> request) {
        StackList stackListReq = request.getRequest();
        int itemCount = stackListReq.getCount();
        int stackTypes = stackListReq.getStacks().size();
        double base = ModConfig.SERVER.stackListXpBase.get();
        double itemDivisor = ModConfig.SERVER.stackListXpItemDivisor.get();
        double itemCap = ModConfig.SERVER.stackListXpItemCap.get();
        double typeDivisor = ModConfig.SERVER.stackListXpTypeDivisor.get();
        double typeCap = ModConfig.SERVER.stackListXpTypeCap.get();
        return base + Math.min((double) itemCount / itemDivisor, itemCap)
                + Math.min((double) stackTypes / typeDivisor, typeCap);
    }

    @Override
    protected String getRequestDescription(IRequest<? extends StackList> request) {
        StackList stackList = request.getRequest();
        if (stackList.getStacks().isEmpty()) {
            return "StackList (empty)";
        }
        ItemStack first = stackList.getStacks().get(0);
        if (stackList.getStacks().size() == 1) {
            return stackList.getCount() + "x " + first.getHoverName().getString();
        }
        return stackList.getCount() + "x " + first.getHoverName().getString() + " (+"
                + (stackList.getStacks().size() - 1) + " more)";
    }
}

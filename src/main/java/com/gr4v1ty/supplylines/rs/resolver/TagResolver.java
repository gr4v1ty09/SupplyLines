package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class TagResolver extends AbstractResolver<RequestTag> {
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
    protected String getRequestDescription(IRequest<? extends RequestTag> request) {
        RequestTag tagReq = request.getRequest();
        return tagReq.getCount() + "x #" + tagReq.getTag().location().getPath();
    }
}

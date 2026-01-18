package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class ToolResolver extends AbstractResolver<Tool> {
    public ToolResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    @Override
    @NotNull
    public TypeToken<? extends Tool> getRequestType() {
        return TypeToken.of(Tool.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Tool> request) {
        return 1; // Tools are always requested one at a time
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Tool> request) {
        return building.hasMatchingToolInNetwork(request.getRequest());
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Tool> request) {
        return building.requestToolFromStockNetwork(request.getRequest(), request.getId());
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Tool> request) {
        return ModConfig.SERVER.toolXpFixed.get();
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Tool> request) {
        Tool tool = request.getRequest();
        return "Tool: " + tool.getEquipmentType().getRegistryName().getPath() + " (L" + tool.getMinLevel() + "-"
                + tool.getMaxLevel() + ")";
    }
}

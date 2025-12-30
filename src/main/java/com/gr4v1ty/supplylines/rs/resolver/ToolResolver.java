package com.gr4v1ty.supplylines.rs.resolver;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.ToolDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.gr4v1ty.supplylines.util.LogTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ToolResolver extends AbstractResolver<Tool> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolResolver.class);

    public ToolResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation) {
        super(resolverId, resolverLocation);
    }

    public ToolResolver(@NotNull UUID resolverId, @NotNull ILocation resolverLocation,
            @NotNull Predicate<IRequester> consumerFilter, int priority,
            @NotNull Function<IRequester, ILocation> intakeLocator,
            @NotNull Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> picker,
            @NotNull ToolDeliveryVerifier verifier) {
        super(resolverId, resolverLocation);
        ResolverComponentRegistry.registerToolComponents(resolverId, new ResolverComponentRegistry.ToolComponents(
                consumerFilter, priority, intakeLocator, picker, verifier));
    }

    @Override
    @NotNull
    public TypeToken<? extends Tool> getRequestType() {
        return TypeToken.of(Tool.class);
    }

    @Override
    protected int getRequiredCount(IRequest<? extends Tool> request) {
        // Tools are always requested one at a time
        return 1;
    }

    @Override
    protected boolean hasComponents() {
        return this.getComponents() != null;
    }

    @Override
    protected int getComponentPriority() {
        ResolverComponentRegistry.ToolComponents comp = this.getComponents();
        return comp != null ? comp.priority : 0;
    }

    @Override
    @Nullable
    protected Predicate<IRequester> getConsumerFilter() {
        ResolverComponentRegistry.ToolComponents comp = this.getComponents();
        return comp != null ? comp.consumerFilter : null;
    }

    @Override
    @Nullable
    protected ILocation getIntakeLocation(IRequester requester) {
        ResolverComponentRegistry.ToolComponents comp = this.getComponents();
        return comp != null ? comp.intakeLocator.apply(requester) : null;
    }

    @Override
    @Nullable
    protected List<DeliveryPlanning.Pick> pickFromRacks(IRequest<? extends Tool> request) {
        ResolverComponentRegistry.ToolComponents comp = this.getComponents();
        return comp != null ? comp.picker.apply(request) : null;
    }

    @Override
    protected boolean isAvailableInNetwork(BuildingStockKeeper building, IRequest<? extends Tool> request) {
        return building.hasMatchingToolInNetwork((Tool) request.getRequest());
    }

    @Override
    protected boolean requestFromStockNetwork(BuildingStockKeeper building, IRequest<? extends Tool> request) {
        return building.requestToolFromStockNetwork((Tool) request.getRequest(), request.getId());
    }

    @Override
    protected boolean verifyDelivery(IRequestManager manager, ILocation dest, IRequest<? extends Tool> request) {
        ResolverComponentRegistry.ToolComponents comp = this.getComponents();
        return comp != null && comp.verifier.isDelivered(manager, dest, request);
    }

    @Override
    protected double calculateSkillXP(IRequest<? extends Tool> request) {
        return 2.0;
    }

    @Override
    protected void logCanResolveRequest(IRequest<? extends Tool> request) {
        LOGGER.debug("{} canResolveRequest: CALLED for request {} (state: {})", LogTags.ORDERING,
                request.getId().toString(), request.getState());
    }

    @Override
    protected void logCannotResolve(IRequest<? extends Tool> request) {
        LOGGER.debug("{} canResolveRequest: NO for request {} (not in staging or network)", LogTags.ORDERING,
                request.getId().toString());
    }

    @Override
    protected void logAttemptResolveRequest(IRequest<? extends Tool> request) {
        Tool tool = request.getRequest();
        LOGGER.debug("{} attemptResolveRequest: STARTING for request {} - tool: {} (level {}-{})", LogTags.ORDERING,
                request.getId().toString(), tool.getEquipmentType().getRegistryName(), tool.getMinLevel(),
                tool.getMaxLevel());
    }

    @Override
    protected void logCannotFulfill(IRequest<? extends Tool> request) {
        LOGGER.debug("{} onRequestCancelled: cannot fulfill request {}", LogTags.ORDERING, request.getId().toString());
    }

    @Override
    protected String getRequestDescription(IRequest<? extends Tool> request) {
        Tool tool = (Tool) request.getRequest();
        return "Tool: " + tool.getEquipmentType().getRegistryName().getPath() + " (L" + tool.getMinLevel() + "-"
                + tool.getMaxLevel() + ")";
    }

    @Nullable
    private ResolverComponentRegistry.ToolComponents getComponents() {
        return ResolverComponentRegistry.getToolComponents((UUID) this.id.getIdentifier());
    }
}

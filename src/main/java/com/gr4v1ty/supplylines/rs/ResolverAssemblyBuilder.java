package com.gr4v1ty.supplylines.rs;

import com.gr4v1ty.supplylines.rs.resolver.FoodResolver;
import com.gr4v1ty.supplylines.rs.resolver.ResolverComponentRegistry;
import com.gr4v1ty.supplylines.rs.resolver.StackListResolver;
import com.gr4v1ty.supplylines.rs.resolver.StackResolver;
import com.gr4v1ty.supplylines.rs.resolver.TagResolver;
import com.gr4v1ty.supplylines.rs.resolver.ToolResolver;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.FoodDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.StackDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.StackListDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.TagDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.ToolDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

public final class ResolverAssemblyBuilder {
    private final IRequestManager manager;
    private final UUID providerId;
    private final ILocation skLocation;
    private Predicate<IRequester> consumerFilter = r -> true;
    private Function<IRequester, ILocation> intakeLocator;
    private int priority = 80;
    private StackResolverConfig stackConfig;
    private ToolResolverConfig toolConfig;
    private TagResolverConfig tagConfig;
    private StackListResolverConfig stackListConfig;
    private FoodResolverConfig foodConfig;

    private ResolverAssemblyBuilder(@NotNull IRequestManager manager, @NotNull UUID providerId,
            @NotNull ILocation skLocation) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.skLocation = Objects.requireNonNull(skLocation, "skLocation");
    }

    public static ResolverAssemblyBuilder create(@NotNull IRequestManager manager, @NotNull UUID providerId,
            @NotNull ILocation skLocation) {
        return new ResolverAssemblyBuilder(manager, providerId, skLocation);
    }

    public ResolverAssemblyBuilder withStackResolver(@NotNull UUID resolverId,
            @NotNull Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> picker,
            @NotNull StackDeliveryVerifier verifier) {
        this.stackConfig = new StackResolverConfig(resolverId, picker, verifier);
        return this;
    }

    public ResolverAssemblyBuilder withToolResolver(@NotNull UUID resolverId,
            @NotNull Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> picker,
            @NotNull ToolDeliveryVerifier verifier) {
        this.toolConfig = new ToolResolverConfig(resolverId, picker, verifier);
        return this;
    }

    public ResolverAssemblyBuilder withTagResolver(@NotNull UUID resolverId,
            @NotNull Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> picker,
            @NotNull TagDeliveryVerifier verifier) {
        this.tagConfig = new TagResolverConfig(resolverId, picker, verifier);
        return this;
    }

    public ResolverAssemblyBuilder withStackListResolver(@NotNull UUID resolverId,
            @NotNull Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> picker,
            @NotNull StackListDeliveryVerifier verifier) {
        this.stackListConfig = new StackListResolverConfig(resolverId, picker, verifier);
        return this;
    }

    public ResolverAssemblyBuilder withFoodResolver(@NotNull UUID resolverId,
            @NotNull Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> picker,
            @NotNull FoodDeliveryVerifier verifier) {
        this.foodConfig = new FoodResolverConfig(resolverId, picker, verifier);
        return this;
    }

    public ResolverAssemblyBuilder consumerFilter(@NotNull Predicate<IRequester> filter) {
        this.consumerFilter = Objects.requireNonNull(filter, "consumerFilter");
        return this;
    }

    public ResolverAssemblyBuilder intakeLocator(@NotNull Function<IRequester, ILocation> locator) {
        this.intakeLocator = Objects.requireNonNull(locator, "intakeLocator");
        return this;
    }

    public ResolverAssemblyBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public void build() {
        Objects.requireNonNull(this.intakeLocator, "intakeLocator must be set");
        if (this.stackConfig == null && this.toolConfig == null && this.tagConfig == null
                && this.stackListConfig == null && this.foodConfig == null) {
            throw new IllegalStateException("At least one resolver type must be configured");
        }
        ArrayList resolvers = new ArrayList();
        if (this.stackConfig != null) {
            resolvers.add(new StackResolver(this.stackConfig.resolverId, this.skLocation, this.consumerFilter,
                    this.priority, this.intakeLocator, this.stackConfig.picker, this.stackConfig.verifier));
        }
        if (this.toolConfig != null) {
            resolvers.add(new ToolResolver(this.toolConfig.resolverId, this.skLocation, this.consumerFilter,
                    this.priority, this.intakeLocator, this.toolConfig.picker, this.toolConfig.verifier));
        }
        if (this.tagConfig != null) {
            resolvers.add(new TagResolver(this.tagConfig.resolverId, this.skLocation, this.consumerFilter,
                    this.priority, this.intakeLocator, this.tagConfig.picker, this.tagConfig.verifier));
        }
        if (this.stackListConfig != null) {
            resolvers.add(new StackListResolver(this.stackListConfig.resolverId, this.skLocation, this.consumerFilter,
                    this.priority, this.intakeLocator, this.stackListConfig.picker, this.stackListConfig.verifier));
        }
        if (this.foodConfig != null) {
            resolvers.add(new FoodResolver(this.foodConfig.resolverId, this.skLocation, this.consumerFilter,
                    this.priority, this.intakeLocator, this.foodConfig.picker, this.foodConfig.verifier));
        }
        SupplyLinesRequestSystem.registerProvider(this.manager, this.providerId, resolvers);
    }

    public static void unregister(@NotNull IRequestManager manager, @NotNull UUID providerId, UUID... resolverIds) {
        SupplyLinesRequestSystem.unregisterProvider(manager, providerId);
        for (UUID resolverId : resolverIds) {
            ResolverComponentRegistry.unregister(resolverId);
        }
    }

    public static class StackResolverConfig {
        public final UUID resolverId;
        public final Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> picker;
        public final StackDeliveryVerifier verifier;

        public StackResolverConfig(UUID resolverId,
                Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> picker,
                StackDeliveryVerifier verifier) {
            this.resolverId = resolverId;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static class ToolResolverConfig {
        public final UUID resolverId;
        public final Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> picker;
        public final ToolDeliveryVerifier verifier;

        public ToolResolverConfig(UUID resolverId,
                Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> picker, ToolDeliveryVerifier verifier) {
            this.resolverId = resolverId;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static class TagResolverConfig {
        public final UUID resolverId;
        public final Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> picker;
        public final TagDeliveryVerifier verifier;

        public TagResolverConfig(UUID resolverId,
                Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> picker,
                TagDeliveryVerifier verifier) {
            this.resolverId = resolverId;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static class StackListResolverConfig {
        public final UUID resolverId;
        public final Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> picker;
        public final StackListDeliveryVerifier verifier;

        public StackListResolverConfig(UUID resolverId,
                Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> picker,
                StackListDeliveryVerifier verifier) {
            this.resolverId = resolverId;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static class FoodResolverConfig {
        public final UUID resolverId;
        public final Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> picker;
        public final FoodDeliveryVerifier verifier;

        public FoodResolverConfig(UUID resolverId,
                Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> picker, FoodDeliveryVerifier verifier) {
            this.resolverId = resolverId;
            this.picker = picker;
            this.verifier = verifier;
        }
    }
}

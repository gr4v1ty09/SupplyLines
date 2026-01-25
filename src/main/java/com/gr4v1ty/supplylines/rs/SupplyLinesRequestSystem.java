package com.gr4v1ty.supplylines.rs;

import com.gr4v1ty.supplylines.rs.factory.BurnableResolverFactory;
import com.gr4v1ty.supplylines.rs.factory.DeliverStackFactory;
import com.gr4v1ty.supplylines.rs.factory.DeliverStackRequestFactory;
import com.gr4v1ty.supplylines.rs.factory.FoodResolverFactory;
import com.gr4v1ty.supplylines.rs.factory.IntakeLocationFactory;
import com.gr4v1ty.supplylines.rs.factory.RackLocationFactory;
import com.gr4v1ty.supplylines.rs.factory.StackListResolverFactory;
import com.gr4v1ty.supplylines.rs.factory.StackResolverFactory;
import com.gr4v1ty.supplylines.rs.factory.TagResolverFactory;
import com.gr4v1ty.supplylines.rs.factory.ToolResolverFactory;
import com.gr4v1ty.supplylines.rs.provider.SupplyLinesResolverProvider;
import com.gr4v1ty.supplylines.rs.request.DeliverStackRequest;
import com.gr4v1ty.supplylines.rs.requestable.DeliverStack;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.manager.RequestMappingHandler;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolverProvider;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SupplyLinesRequestSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplyLinesRequestSystem.class);

    private SupplyLinesRequestSystem() {
    }

    public static void registerFactories() {
        StandardFactoryController controller = StandardFactoryController.getInstance();
        if (controller == null) {
            throw new IllegalStateException("StandardFactoryController is not initialized yet!");
        }
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, DeliverStackFactory::new,
                "DeliverStackFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, DeliverStackRequestFactory::new,
                "DeliverStackRequestFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, FoodResolverFactory::new,
                "FoodResolverFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, IntakeLocationFactory::new,
                "IntakeLocationFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, RackLocationFactory::new,
                "RackLocationFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, StackListResolverFactory::new,
                "StackListResolverFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, StackResolverFactory::new,
                "StackResolverFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, TagResolverFactory::new,
                "TagResolverFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, ToolResolverFactory::new,
                "ToolResolverFactory");
        SupplyLinesRequestSystem.registerFactory((IFactoryController) controller, BurnableResolverFactory::new,
                "BurnableResolverFactory");
        try {
            RequestMappingHandler.registerRequestableTypeMapping(DeliverStack.class, DeliverStackRequest.class);
            LOGGER.debug("Registered DeliverStack requestable type mapping -> DeliverStackRequest");
        } catch (Exception e) {
            LOGGER.error("Failed to register requestable type mappings", e);
            throw e;
        }
        LOGGER.debug("All Request System factories registered successfully");
    }

    private static void registerFactory(IFactoryController controller, Supplier<?> factorySupplier,
            String factoryName) {
        try {
            Object factory = factorySupplier.get();
            controller.registerNewFactory((IFactory<?, ?>) factory);
            LOGGER.debug("Registered {}", factoryName);
        } catch (Exception e) {
            LOGGER.error("Failed to register {}", factoryName, e);
            throw e;
        }
    }

    /**
     * Registers a provider with MineColonies' request system. Caller
     * (RequestHandler) is responsible for tracking registration state.
     */
    public static void registerProvider(@NotNull IRequestManager manager, @NotNull UUID providerId,
            @NotNull List<IRequestResolver<?>> resolvers) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(resolvers, "resolvers");

        SupplyLinesResolverProvider provider = new SupplyLinesResolverProvider(providerId, resolvers);

        try {
            manager.onProviderAddedToColony((IRequestResolverProvider) provider);
            LOGGER.info("[SupplyLines] Provider {} registered with {} resolvers", providerId, resolvers.size());
        } catch (IllegalArgumentException e) {
            // Provider already registered from deserialization - replace it with fresh
            // resolvers
            // that have components registered in the static registry
            SupplyLinesResolverProvider empty = new SupplyLinesResolverProvider(providerId, List.of());
            try {
                manager.onProviderRemovedFromColony((IRequestResolverProvider) empty);
            } catch (IllegalArgumentException ignored) {
            }
            try {
                manager.onProviderAddedToColony((IRequestResolverProvider) provider);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * Unregisters a provider from MineColonies' request system.
     */
    public static void unregisterProvider(@NotNull IRequestManager manager, @NotNull UUID providerId) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(providerId, "providerId");

        SupplyLinesResolverProvider provider = new SupplyLinesResolverProvider(providerId, List.of());
        try {
            manager.onProviderRemovedFromColony((IRequestResolverProvider) provider);
        } catch (IllegalArgumentException ignored) {
        }
    }
}

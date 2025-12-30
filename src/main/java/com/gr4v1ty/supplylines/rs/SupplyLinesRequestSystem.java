package com.gr4v1ty.supplylines.rs;

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
            controller.registerNewFactory((IFactory) factory);
            LOGGER.debug("Registered {}", factoryName);
        } catch (Exception e) {
            LOGGER.error("Failed to register {}", factoryName, e);
            throw e;
        }
    }

    /**
     * Registers a provider with MineColonies' request system. This method is
     * idempotent - safe to call multiple times. MineColonies will throw
     * IllegalArgumentException if already registered, which we catch and ignore.
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
            // Provider already registered - this is expected and safe to ignore
        } catch (Exception e) {
            LOGGER.error("[SupplyLines] Failed to register provider {}", providerId, e);
            throw e;
        }
    }

    /**
     * Unregisters a provider from MineColonies' request system. This method handles
     * the case where the provider may not be registered gracefully.
     *
     * Note: We create an empty provider with the same ID - MineColonies uses the ID
     * for removal, not the resolver list contents.
     */
    public static void unregisterProvider(@NotNull IRequestManager manager, @NotNull UUID providerId) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(providerId, "providerId");

        // Create provider with same ID - MineColonies matches by ID for removal
        SupplyLinesResolverProvider provider = new SupplyLinesResolverProvider(providerId, List.of());

        try {
            manager.onProviderRemovedFromColony((IRequestResolverProvider) provider);
            LOGGER.info("[SupplyLines] Provider {} unregistered", providerId);
        } catch (IllegalArgumentException e) {
            // Provider wasn't registered - this can happen on world reload
            LOGGER.debug("[SupplyLines] Provider {} was not registered (already removed)", providerId);
        } catch (Exception e) {
            LOGGER.error("[SupplyLines] Failed to unregister provider {}", providerId, e);
            // Don't rethrow - we're cleaning up, best effort
        }
    }

    /**
     * Replaces resolvers for a provider. Unregisters the old provider and registers
     * new one.
     */
    public static void replaceResolvers(@NotNull IRequestManager manager, @NotNull UUID providerId,
            @NotNull List<IRequestResolver<?>> newResolvers) {
        LOGGER.debug("[SupplyLines] Replacing resolvers for provider {}", providerId);
        SupplyLinesRequestSystem.unregisterProvider(manager, providerId);
        SupplyLinesRequestSystem.registerProvider(manager, providerId, newResolvers);
    }
}

package com.gr4v1ty.supplylines.rs.resolver;

import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.DeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic components record that replaces the 6 type-specific component
 * classes. Holds all the configurable parts of a resolver that are shared
 * across all types. Also provides a static registry for component lookup by
 * resolver UUID.
 *
 * @param <T>
 *            The requestable type this component handles
 */
public record ResolverComponents<T extends IRequestable>(@NotNull Predicate<IRequester> consumerFilter, int priority,
        @NotNull Function<IRequester, ILocation> intakeLocator,
        @NotNull Function<IRequest<? extends T>, List<DeliveryPlanning.Pick>> picker,
        @NotNull DeliveryVerifier<T> verifier) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolverComponents.class);
    private static final Map<UUID, ResolverComponents<?>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * Registers components for a resolver ID. Components must be registered before
     * the resolver attempts to use them.
     */
    public static void register(@NotNull UUID resolverId, @NotNull ResolverComponents<?> components) {
        REGISTRY.put(resolverId, components);
        LOGGER.trace("[Ordering] Registered components for resolver {}, registry size now {}", resolverId,
                REGISTRY.size());
    }

    /**
     * Retrieves components for a resolver ID.
     *
     * @return The components, or null if not registered
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends IRequestable> ResolverComponents<T> get(@NotNull UUID resolverId) {
        return (ResolverComponents<T>) REGISTRY.get(resolverId);
    }

    /**
     * Unregisters components for a resolver ID. Called when building is destroyed.
     */
    public static void unregister(@NotNull UUID resolverId) {
        REGISTRY.remove(resolverId);
    }

    /**
     * Clears all registered components. Useful for world unload.
     */
    public static void clear() {
        REGISTRY.clear();
    }
}

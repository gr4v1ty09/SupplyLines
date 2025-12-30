package com.gr4v1ty.supplylines.rs.resolver;

import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.gr4v1ty.supplylines.rs.verifier.FoodDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.StackDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.StackListDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.TagDeliveryVerifier;
import com.gr4v1ty.supplylines.rs.verifier.ToolDeliveryVerifier;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResolverComponentRegistry {
    private static final Map<UUID, StackComponents> stackRegistry = new ConcurrentHashMap<UUID, StackComponents>();
    private static final Map<UUID, ToolComponents> toolRegistry = new ConcurrentHashMap<UUID, ToolComponents>();
    private static final Map<UUID, TagComponents> tagRegistry = new ConcurrentHashMap<UUID, TagComponents>();
    private static final Map<UUID, StackListComponents> stackListRegistry = new ConcurrentHashMap<UUID, StackListComponents>();
    private static final Map<UUID, FoodComponents> foodRegistry = new ConcurrentHashMap<UUID, FoodComponents>();

    private ResolverComponentRegistry() {
    }

    public static void registerStackComponents(@NotNull UUID resolverId, @NotNull StackComponents components) {
        stackRegistry.put(resolverId, components);
    }

    public static void registerToolComponents(@NotNull UUID resolverId, @NotNull ToolComponents components) {
        toolRegistry.put(resolverId, components);
    }

    public static void registerTagComponents(@NotNull UUID resolverId, @NotNull TagComponents components) {
        tagRegistry.put(resolverId, components);
    }

    public static void registerStackListComponents(@NotNull UUID resolverId, @NotNull StackListComponents components) {
        stackListRegistry.put(resolverId, components);
    }

    public static void registerFoodComponents(@NotNull UUID resolverId, @NotNull FoodComponents components) {
        foodRegistry.put(resolverId, components);
    }

    @Nullable
    public static StackComponents getStackComponents(@NotNull UUID resolverId) {
        return stackRegistry.get(resolverId);
    }

    @Nullable
    public static ToolComponents getToolComponents(@NotNull UUID resolverId) {
        return toolRegistry.get(resolverId);
    }

    @Nullable
    public static TagComponents getTagComponents(@NotNull UUID resolverId) {
        return tagRegistry.get(resolverId);
    }

    @Nullable
    public static StackListComponents getStackListComponents(@NotNull UUID resolverId) {
        return stackListRegistry.get(resolverId);
    }

    @Nullable
    public static FoodComponents getFoodComponents(@NotNull UUID resolverId) {
        return foodRegistry.get(resolverId);
    }

    public static void unregister(@NotNull UUID resolverId) {
        stackRegistry.remove(resolverId);
        toolRegistry.remove(resolverId);
        tagRegistry.remove(resolverId);
        stackListRegistry.remove(resolverId);
        foodRegistry.remove(resolverId);
    }

    public static void clear() {
        stackRegistry.clear();
        toolRegistry.clear();
        tagRegistry.clear();
        stackListRegistry.clear();
        foodRegistry.clear();
    }

    public static final class StackComponents {
        public final Predicate<IRequester> consumerFilter;
        public final int priority;
        public final Function<IRequester, ILocation> intakeLocator;
        public final Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> picker;
        public final StackDeliveryVerifier verifier;

        public StackComponents(@NotNull Predicate<IRequester> consumerFilter, int priority,
                @NotNull Function<IRequester, ILocation> intakeLocator,
                @NotNull Function<IRequest<? extends Stack>, List<DeliveryPlanning.Pick>> picker,
                @NotNull StackDeliveryVerifier verifier) {
            this.consumerFilter = consumerFilter;
            this.priority = priority;
            this.intakeLocator = intakeLocator;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static final class ToolComponents {
        public final Predicate<IRequester> consumerFilter;
        public final int priority;
        public final Function<IRequester, ILocation> intakeLocator;
        public final Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> picker;
        public final ToolDeliveryVerifier verifier;

        public ToolComponents(@NotNull Predicate<IRequester> consumerFilter, int priority,
                @NotNull Function<IRequester, ILocation> intakeLocator,
                @NotNull Function<IRequest<? extends Tool>, List<DeliveryPlanning.Pick>> picker,
                @NotNull ToolDeliveryVerifier verifier) {
            this.consumerFilter = consumerFilter;
            this.priority = priority;
            this.intakeLocator = intakeLocator;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static final class TagComponents {
        public final Predicate<IRequester> consumerFilter;
        public final int priority;
        public final Function<IRequester, ILocation> intakeLocator;
        public final Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> picker;
        public final TagDeliveryVerifier verifier;

        public TagComponents(@NotNull Predicate<IRequester> consumerFilter, int priority,
                @NotNull Function<IRequester, ILocation> intakeLocator,
                @NotNull Function<IRequest<? extends RequestTag>, List<DeliveryPlanning.Pick>> picker,
                @NotNull TagDeliveryVerifier verifier) {
            this.consumerFilter = consumerFilter;
            this.priority = priority;
            this.intakeLocator = intakeLocator;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static final class StackListComponents {
        public final Predicate<IRequester> consumerFilter;
        public final int priority;
        public final Function<IRequester, ILocation> intakeLocator;
        public final Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> picker;
        public final StackListDeliveryVerifier verifier;

        public StackListComponents(@NotNull Predicate<IRequester> consumerFilter, int priority,
                @NotNull Function<IRequester, ILocation> intakeLocator,
                @NotNull Function<IRequest<? extends StackList>, List<DeliveryPlanning.Pick>> picker,
                @NotNull StackListDeliveryVerifier verifier) {
            this.consumerFilter = consumerFilter;
            this.priority = priority;
            this.intakeLocator = intakeLocator;
            this.picker = picker;
            this.verifier = verifier;
        }
    }

    public static final class FoodComponents {
        public final Predicate<IRequester> consumerFilter;
        public final int priority;
        public final Function<IRequester, ILocation> intakeLocator;
        public final Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> picker;
        public final FoodDeliveryVerifier verifier;

        public FoodComponents(@NotNull Predicate<IRequester> consumerFilter, int priority,
                @NotNull Function<IRequester, ILocation> intakeLocator,
                @NotNull Function<IRequest<? extends Food>, List<DeliveryPlanning.Pick>> picker,
                @NotNull FoodDeliveryVerifier verifier) {
            this.consumerFilter = consumerFilter;
            this.priority = priority;
            this.intakeLocator = intakeLocator;
            this.picker = picker;
            this.verifier = verifier;
        }
    }
}

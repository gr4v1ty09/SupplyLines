package com.gr4v1ty.supplylines.rs.provider;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolverProvider;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class SupplyLinesResolverProvider implements IRequestResolverProvider {
    @NotNull
    private final IToken<UUID> id;
    @NotNull
    private final ImmutableList<IRequestResolver<?>> resolvers;

    public SupplyLinesResolverProvider(@NotNull UUID providerId, @NotNull List<IRequestResolver<?>> resolvers) {
        this.id = new StandardToken(Objects.requireNonNull(providerId, "providerId"));
        this.resolvers = ImmutableList.copyOf((Collection) Objects.requireNonNull(resolvers, "resolvers"));
    }

    @NotNull
    public IToken<?> getId() {
        return this.id;
    }

    @NotNull
    public ImmutableCollection<IRequestResolver<?>> getResolvers() {
        return this.resolvers;
    }
}

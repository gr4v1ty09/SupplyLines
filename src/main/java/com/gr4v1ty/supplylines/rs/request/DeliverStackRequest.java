package com.gr4v1ty.supplylines.rs.request;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.requestable.DeliverStack;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.requests.AbstractRequest;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class DeliverStackRequest extends AbstractRequest<DeliverStack> {
    public DeliverStackRequest(@NotNull IRequester requester, @NotNull IToken<?> token,
            @NotNull DeliverStack requested) {
        super(requester, token, requested);
    }

    public DeliverStackRequest(@NotNull IRequester requester, @NotNull IToken<?> token, @NotNull RequestState state,
            @NotNull DeliverStack requested) {
        super(requester, token, state, requested);
    }

    @NotNull
    public Set<TypeToken<?>> getSuperClasses() {
        return ImmutableSet.<TypeToken<?>>of(TypeToken.of(DeliverStackRequest.class),
                TypeToken.of(AbstractRequest.class));
    }

    @NotNull
    public Component getShortDisplayString() {
        DeliverStack ds = (DeliverStack) this.getRequest();
        return Component.literal((String) String.format("Deliver %s x%d from %s to %s",
                ds.getPayload().getStack().getDisplayName().getString(), ds.getCount(),
                ds.getSource().getInDimensionLocation(), ds.getDest().getInDimensionLocation()));
    }
}
